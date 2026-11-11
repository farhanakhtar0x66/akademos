-- =====================================================================
--  Akademos · update 003: who-sees-what, daily class check-in, "I've paid"
--  Run once in Supabase → SQL Editor, AFTER schema.sql and 002.
--
--  1. Teachers only see and edit the batches they teach (their students,
--     their subjects). The administrator still sees everything.
--  2. Teachers confirm their classes for the day (the 3 PM check-in).
--  3. Students/parents can say "I've paid"; the fee only counts as paid
--     once the administrator confirms it.
--  4. A teacher's access key can hand them their batches; per-class pay
--     rates and a count of the classes each teacher took.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Teacher scoping
-- ---------------------------------------------------------------------
-- Does the signed-in person teach this batch? (The administrator counts as teaching everything.)
create or replace function public.teaches(b uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select public.is_admin()
      or (coalesce(public.my_role() = 'teacher', false)
          and exists (select 1 from public.batches where id = b and teacher_id = auth.uid()))
$$;

create or replace function public.teaches_student(sid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select public.is_admin()
      or (coalesce(public.my_role() = 'teacher', false)
          and exists (select 1 from public.batch_students bs join public.batches b on b.id = bs.batch_id
                       where bs.student_id = sid and b.teacher_id = auth.uid()))
$$;

create or replace function public.teaches_assessment(a uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select public.is_admin()
      or (coalesce(public.my_role() = 'teacher', false)
          and exists (select 1 from public.assessments x join public.batches b on b.id = x.batch_id
                       where x.id = a and b.teacher_id = auth.uid()))
$$;

-- Storage paths are text (<student_id>/<test_id>/<file>), so compare as text.
create or replace function public.teaches_assessment_text(a text) returns boolean
language sql stable security definer set search_path = public as $$
  select public.is_admin()
      or (coalesce(public.my_role() = 'teacher', false)
          and exists (select 1 from public.assessments x join public.batches b on b.id = x.batch_id
                       where x.id::text = a and b.teacher_id = auth.uid()))
$$;

-- Is this student actually in the batch the test belongs to?
create or replace function public.in_test_batch(a uuid, sid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.assessments x join public.batch_students bs on bs.batch_id = x.batch_id
                  where x.id = a and bs.student_id = sid)
$$;

-- Does one of my children study in this batch?
create or replace function public.parent_of_batch(b uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.my_role() = 'parent', false)
     and exists (select 1 from public.batch_students bs join public.parent_students ps on ps.student_id = bs.student_id
                  where bs.batch_id = b and ps.parent_id = auth.uid())
$$;

do $$
declare f text;
begin
  foreach f in array array['teaches(uuid)', 'teaches_student(uuid)', 'teaches_assessment(uuid)',
                           'teaches_assessment_text(text)', 'in_test_batch(uuid, uuid)', 'parent_of_batch(uuid)'] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;

-- People: staff see the staff list; only the administrator sees parents.
drop policy if exists profiles_read on public.profiles;
create policy profiles_read on public.profiles for select to authenticated
  using (id = auth.uid() or public.is_admin() or (public.is_staff() and role in ('admin', 'teacher')));

drop policy if exists batches_read on public.batches;
create policy batches_read on public.batches for select to authenticated
  using (public.teaches(id) or public.parent_of_batch(id));

drop policy if exists slots_read on public.schedule_slots;
create policy slots_read on public.schedule_slots for select to authenticated
  using (public.teaches(batch_id) or public.parent_of_batch(batch_id));

drop policy if exists students_read on public.students;
create policy students_read on public.students for select to authenticated
  using (public.teaches_student(id) or public.is_parent_of(id));

drop policy if exists batch_students_read on public.batch_students;
create policy batch_students_read on public.batch_students for select to authenticated
  using (public.teaches(batch_id) or public.is_parent_of(student_id));

drop policy if exists attendance_read on public.attendance;
drop policy if exists attendance_insert on public.attendance;
drop policy if exists attendance_update on public.attendance;
create policy attendance_read on public.attendance for select to authenticated
  using (public.teaches(batch_id) or public.is_parent_of(student_id));
create policy attendance_insert on public.attendance for insert to authenticated
  with check (public.teaches(batch_id)
              and exists (select 1 from public.batch_students bs where bs.batch_id = attendance.batch_id and bs.student_id = attendance.student_id));
create policy attendance_update on public.attendance for update to authenticated
  using (public.teaches(batch_id)) with check (public.teaches(batch_id));

drop policy if exists assessments_read on public.assessments;
drop policy if exists assessments_insert on public.assessments;
drop policy if exists assessments_update on public.assessments;
create policy assessments_read on public.assessments for select to authenticated
  using (public.teaches(batch_id) or public.parent_of_batch(batch_id));
create policy assessments_insert on public.assessments for insert to authenticated with check (public.teaches(batch_id));
create policy assessments_update on public.assessments for update to authenticated
  using (public.teaches(batch_id)) with check (public.teaches(batch_id));

drop policy if exists marks_read on public.marks;
drop policy if exists marks_insert on public.marks;
drop policy if exists marks_update on public.marks;
create policy marks_read on public.marks for select to authenticated
  using (public.teaches_assessment(assessment_id) or public.is_parent_of(student_id));
create policy marks_insert on public.marks for insert to authenticated
  with check (public.teaches_assessment(assessment_id) and public.in_test_batch(assessment_id, student_id));
create policy marks_update on public.marks for update to authenticated
  using (public.teaches_assessment(assessment_id))
  with check (public.teaches_assessment(assessment_id) and public.in_test_batch(assessment_id, student_id));

drop policy if exists parent_students_read on public.parent_students;
create policy parent_students_read on public.parent_students for select to authenticated
  using (parent_id = auth.uid() or public.is_admin());

-- Answer sheets: a teacher can only open or upload sheets for their own tests.
drop policy if exists answer_sheets_read on storage.objects;
drop policy if exists answer_sheets_insert on storage.objects;
drop policy if exists answer_sheets_update on storage.objects;
create policy answer_sheets_read on storage.objects for select to authenticated
  using (bucket_id = 'answer-sheets'
         and (public.teaches_assessment_text((storage.foldername(name))[2])
              or public.is_parent_of_text((storage.foldername(name))[1])));
create policy answer_sheets_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'answer-sheets' and public.teaches_assessment_text((storage.foldername(name))[2]));
create policy answer_sheets_update on storage.objects for update to authenticated
  using (bucket_id = 'answer-sheets' and public.teaches_assessment_text((storage.foldername(name))[2]))
  with check (bucket_id = 'answer-sheets' and public.teaches_assessment_text((storage.foldername(name))[2]));

-- Remove someone's access for good (they would need a new key to come back).
create or replace function public.remove_user(p_user uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'Not allowed' using errcode = '42501'; end if;
  if p_user = auth.uid() then raise exception 'You cannot remove your own account'; end if;
  delete from public.profiles where id = p_user;
end $$;
revoke all on function public.remove_user(uuid) from public, anon;
grant execute on function public.remove_user(uuid) to authenticated;

-- ---------------------------------------------------------------------
-- 2. Daily class check-in
--    No row for a day = the teacher's usual timetable (their batches' weekly timings).
--    mode 'usual'  = confirmed the usual timetable
--    mode 'custom' = today's classes are the rows in day_classes
--    mode 'off'    = no class today
-- ---------------------------------------------------------------------
create table public.day_plans (
  teacher_id  uuid not null references public.profiles(id) on delete cascade,
  date        date not null,
  mode        text not null check (mode in ('usual', 'custom', 'off')),
  updated_at  timestamptz not null default now(),
  primary key (teacher_id, date)
);

create table public.day_classes (
  id          uuid primary key default gen_random_uuid(),
  teacher_id  uuid not null references public.profiles(id) on delete cascade,
  date        date not null,
  batch_id    uuid not null references public.batches(id) on delete cascade,
  start_time  time not null,
  end_time    time not null,
  created_at  timestamptz not null default now(),
  check (end_time > start_time)
);
create index on public.day_classes (date);
create index on public.day_plans (date);

create or replace function public.stamp_day_plan() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  new.updated_at := now();
  return new;
end $$;
create trigger trg_stamp_day_plans before insert or update on public.day_plans for each row execute function public.stamp_day_plan();
create trigger trg_audit_day_plans after insert or update or delete on public.day_plans for each row execute function public.audit();
create trigger trg_audit_day_classes after insert or update or delete on public.day_classes for each row execute function public.audit();

alter table public.day_plans   enable row level security;
alter table public.day_classes enable row level security;

-- A teacher reads and plans only their own days (today up to two weeks ahead); the administrator sees and can change anyone's.
create policy day_plans_read on public.day_plans for select to authenticated
  using (public.is_admin() or (teacher_id = auth.uid() and public.is_staff()));
create policy day_plans_write on public.day_plans for all to authenticated
  using (public.is_admin() or (teacher_id = auth.uid() and public.is_staff()))
  with check (public.is_admin()
              or (teacher_id = auth.uid() and public.is_staff() and date between current_date and current_date + 14));

create policy day_classes_read on public.day_classes for select to authenticated
  using (public.is_admin() or (teacher_id = auth.uid() and public.is_staff()));
create policy day_classes_write on public.day_classes for all to authenticated
  using (public.is_admin() or (teacher_id = auth.uid() and public.is_staff()))
  with check (public.is_admin()
              or (teacher_id = auth.uid() and public.teaches(batch_id) and date between current_date and current_date + 14));

-- ---------------------------------------------------------------------
-- 3. "I've paid" claims
-- ---------------------------------------------------------------------
create table public.fee_claims (
  id           uuid primary key default gen_random_uuid(),
  student_id   uuid not null references public.students(id) on delete cascade,
  for_month    date not null check (extract(day from for_month) = 1),
  amount       numeric(10,2) not null check (amount > 0 and amount <= 10000000),
  method       text not null default 'upi' check (method in ('cash', 'upi', 'bank', 'cheque', 'other')),
  note         text check (char_length(note) <= 200),
  status       text not null default 'pending' check (status in ('pending', 'confirmed', 'rejected')),
  claimed_by   uuid references public.profiles(id) on delete set null,
  created_at   timestamptz not null default now(),
  resolved_by  uuid references public.profiles(id) on delete set null,
  resolved_at  timestamptz,
  payment_id   uuid references public.fee_payments(id) on delete set null
);
-- Only one open claim per child per month.
create unique index fee_claims_one_open on public.fee_claims (student_id, for_month) where status = 'pending';
create index on public.fee_claims (status);

-- A new claim always starts as "waiting"; nobody can mark their own claim as confirmed.
create or replace function public.stamp_fee_claim() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  new.claimed_by := auth.uid();
  new.status := 'pending';
  new.created_at := now();
  new.resolved_by := null; new.resolved_at := null; new.payment_id := null;
  return new;
end $$;
create trigger trg_stamp_fee_claims before insert on public.fee_claims for each row execute function public.stamp_fee_claim();
create trigger trg_audit_fee_claims after insert or update or delete on public.fee_claims for each row execute function public.audit();

alter table public.fee_claims enable row level security;
create policy fee_claims_read on public.fee_claims for select to authenticated
  using (public.is_admin() or public.is_parent_of(student_id));
create policy fee_claims_insert on public.fee_claims for insert to authenticated
  with check (public.is_parent_of(student_id));
create policy fee_claims_delete on public.fee_claims for delete to authenticated
  using (public.is_admin());
-- No update policy: claims change only through the two functions below.

create or replace function public.confirm_fee_claim(p_id uuid, p_amount numeric default null) returns void
language plpgsql security definer set search_path = public as $$
declare c public.fee_claims; v_pay uuid;
begin
  if not public.is_admin() then raise exception 'Not allowed' using errcode = '42501'; end if;
  select * into c from public.fee_claims where id = p_id and status = 'pending' for update;
  if not found then raise exception 'This was already handled'; end if;
  insert into public.fee_payments (student_id, for_month, amount, paid_on, method, note)
  values (c.student_id, c.for_month, coalesce(p_amount, c.amount), c.created_at::date, c.method,
          coalesce(nullif(c.note, ''), 'Reported paid in the app'))
  returning id into v_pay;
  update public.fee_claims set status = 'confirmed', resolved_by = auth.uid(), resolved_at = now(), payment_id = v_pay
   where id = p_id;
end $$;

create or replace function public.reject_fee_claim(p_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'Not allowed' using errcode = '42501'; end if;
  update public.fee_claims set status = 'rejected', resolved_by = auth.uid(), resolved_at = now()
   where id = p_id and status = 'pending';
  if not found then raise exception 'This was already handled'; end if;
end $$;

revoke all on function public.confirm_fee_claim(uuid, numeric) from public, anon;
revoke all on function public.reject_fee_claim(uuid) from public, anon;
grant execute on function public.confirm_fee_claim(uuid, numeric) to authenticated;
grant execute on function public.reject_fee_claim(uuid) to authenticated;

grant select, insert, update, delete on public.day_plans, public.day_classes, public.fee_claims to authenticated;
revoke all on public.day_plans, public.day_classes, public.fee_claims from anon;

-- ---------------------------------------------------------------------
-- 4. Teacher keys that hand over batches, and pay per class
-- ---------------------------------------------------------------------
alter table public.invites add column if not exists batch_ids uuid[] not null default '{}';

drop function if exists public.create_invite(public.app_role, text, uuid[], int);
create or replace function public.create_invite(p_role public.app_role, p_label text,
  p_student_ids uuid[] default '{}', p_valid_hours int default 48, p_batch_ids uuid[] default '{}') returns text
language plpgsql security definer set search_path = public, extensions as $$
declare v_code text;
begin
  if not public.is_admin() then
    raise exception 'Only the administrator can create access keys' using errcode = '42501';
  end if;
  if p_role = 'admin' then
    raise exception 'Administrator keys can only be made from the Supabase SQL editor';
  end if;
  if p_role = 'parent' and coalesce(array_length(p_student_ids, 1), 0) = 0 then
    raise exception 'Choose at least one student for a parent key';
  end if;
  if p_valid_hours not between 1 and 720 then
    raise exception 'Validity must be between 1 hour and 30 days';
  end if;
  v_code := public._make_invite(p_role, p_label, p_student_ids, p_valid_hours);
  if p_role = 'teacher' and coalesce(array_length(p_batch_ids, 1), 0) > 0 then
    update public.invites set batch_ids = p_batch_ids
     where code_hash = encode(digest(replace(v_code, '-', ''), 'sha256'), 'hex');
  end if;
  return v_code;
end $$;
revoke all on function public.create_invite(public.app_role, text, uuid[], int, uuid[]) from public, anon;
grant execute on function public.create_invite(public.app_role, text, uuid[], int, uuid[]) to authenticated;

-- Same as before, plus: a teacher key makes its new owner the teacher of the batches chosen on it.
create or replace function public.redeem_invite(p_code text, p_full_name text) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare
  v_uid uuid := auth.uid();
  v_norm text;
  v_inv public.invites;
  v_prof public.profiles;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'error', 'Please sign in first');
  end if;
  if (select count(*) from public.redeem_attempts
      where user_id = v_uid and attempted_at > now() - interval '15 minutes') >= 5 then
    return jsonb_build_object('ok', false, 'error', 'Too many wrong keys. Please wait 15 minutes and try again.');
  end if;

  v_norm := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  select * into v_inv from public.invites
   where code_hash = encode(digest(v_norm, 'sha256'), 'hex')
     and used_at is null and expires_at > now()
   for update;

  if not found then
    insert into public.redeem_attempts (user_id) values (v_uid);
    return jsonb_build_object('ok', false, 'error', 'This key is not valid, has expired, or was already used.');
  end if;

  select * into v_prof from public.profiles where id = v_uid;
  if found then
    if v_prof.role = 'parent' and v_inv.role = 'parent' and v_prof.active then
      insert into public.parent_students (parent_id, student_id)
      select v_uid, unnest(v_inv.student_ids) on conflict do nothing;
    else
      return jsonb_build_object('ok', false, 'error', 'This account already has access. Use a separate account for a different role.');
    end if;
  else
    if char_length(trim(coalesce(p_full_name, ''))) < 2 then
      return jsonb_build_object('ok', false, 'error', 'Please enter your full name');
    end if;
    insert into public.profiles (id, full_name, role) values (v_uid, trim(p_full_name), v_inv.role);
    if v_inv.role = 'parent' then
      insert into public.parent_students (parent_id, student_id)
      select v_uid, unnest(v_inv.student_ids) on conflict do nothing;
    end if;
    if v_inv.role = 'teacher' and coalesce(array_length(v_inv.batch_ids, 1), 0) > 0 then
      update public.batches set teacher_id = v_uid where id = any (v_inv.batch_ids);
    end if;
  end if;

  update public.invites set used_by = v_uid, used_at = now() where id = v_inv.id;
  return jsonb_build_object('ok', true, 'role', v_inv.role);
end $$;
revoke all on function public.redeem_invite(text, text) from public, anon;
grant execute on function public.redeem_invite(text, text) to authenticated;

-- What the administrator pays each teacher for one class.
create table public.teacher_rates (
  teacher_id  uuid primary key references public.profiles(id) on delete cascade,
  per_class   numeric(10,2) not null check (per_class >= 0 and per_class <= 1000000),
  updated_at  timestamptz not null default now()
);
create trigger trg_audit_teacher_rates after insert or update or delete on public.teacher_rates for each row execute function public.audit();
alter table public.teacher_rates enable row level security;
create policy teacher_rates_admin on public.teacher_rates for all to authenticated
  using (public.is_admin()) with check (public.is_admin());
grant select, insert, update, delete on public.teacher_rates to authenticated;
revoke all on public.teacher_rates from anon;

-- Classes actually taken (attendance saved) between two dates, one row per class.
-- The class counts for the teacher who took attendance; if the administrator took it,
-- it counts for the batch's teacher.
create or replace function public.classes_taken(p_from date, p_to date)
returns table (teacher_id uuid, batch_id uuid, date date, present int, total int)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'Not allowed' using errcode = '42501'; end if;
  return query
  select coalesce(max(case when p.role = 'teacher' then a.marked_by::text end)::uuid, b.teacher_id, max(a.marked_by::text)::uuid),
         a.batch_id, a.date,
         count(*) filter (where a.status <> 'absent')::int, count(*)::int
    from public.attendance a
    join public.batches b on b.id = a.batch_id
    left join public.profiles p on p.id = a.marked_by
   where a.date between p_from and p_to
   group by a.batch_id, a.date, b.teacher_id;
end $$;
revoke all on function public.classes_taken(date, date) from public, anon;
grant execute on function public.classes_taken(date, date) to authenticated;
