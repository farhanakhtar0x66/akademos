-- =====================================================================
--  Akademos · Global Institute — database, security rules and storage
--  Run this whole file once in Supabase → SQL Editor → New query → Run.
--  It is safe to re-run on an empty project only.
-- =====================================================================

create extension if not exists pgcrypto with schema extensions;

do $$ begin
  create type public.app_role as enum ('admin', 'teacher', 'parent');
exception when duplicate_object then null; end $$;

-- ---------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------
create table public.profiles (
  id          uuid primary key references auth.users(id) on delete cascade,
  full_name   text not null check (char_length(full_name) between 2 and 80),
  role        public.app_role not null,
  active      boolean not null default true,
  created_at  timestamptz not null default now()
);

create table public.classes (
  id          uuid primary key default gen_random_uuid(),
  name        text not null unique check (char_length(name) between 1 and 40),
  sort_order  int not null default 0,
  created_at  timestamptz not null default now()
);

create table public.subjects (
  id          uuid primary key default gen_random_uuid(),
  name        text not null unique check (char_length(name) between 1 and 40),
  color       text not null default '#1F4E8C' check (color ~ '^#[0-9A-Fa-f]{6}$'),
  created_at  timestamptz not null default now()
);

create table public.students (
  id              uuid primary key default gen_random_uuid(),
  full_name       text not null check (char_length(full_name) between 2 and 80),
  roll_no         text check (char_length(roll_no) <= 20),
  class_id        uuid references public.classes(id) on delete set null,
  guardian_phone  text check (char_length(guardian_phone) <= 20),
  active          boolean not null default true,
  created_at      timestamptz not null default now()
);

-- A batch = a group of students who attend one subject together
-- (e.g. "Class 10 · English"). Each batch has its own weekly timings.
create table public.batches (
  id          uuid primary key default gen_random_uuid(),
  name        text not null check (char_length(name) between 1 and 60),
  class_id    uuid references public.classes(id) on delete cascade,
  subject_id  uuid references public.subjects(id) on delete cascade,
  teacher_id  uuid references public.profiles(id) on delete set null,
  created_at  timestamptz not null default now()
);

create table public.batch_students (
  batch_id    uuid not null references public.batches(id) on delete cascade,
  student_id  uuid not null references public.students(id) on delete cascade,
  primary key (batch_id, student_id)
);

create table public.schedule_slots (
  id          uuid primary key default gen_random_uuid(),
  batch_id    uuid not null references public.batches(id) on delete cascade,
  weekday     smallint not null check (weekday between 1 and 7),   -- 1 = Monday … 7 = Sunday
  start_time  time not null,
  end_time    time not null,
  check (end_time > start_time)
);

create table public.attendance (
  id          uuid primary key default gen_random_uuid(),
  batch_id    uuid not null references public.batches(id) on delete cascade,
  student_id  uuid not null references public.students(id) on delete cascade,
  date        date not null,
  status      text not null check (status in ('present', 'absent', 'late')),
  arrived_at  timestamptz,
  left_at     timestamptz,
  marked_by   uuid references public.profiles(id) on delete set null,
  updated_at  timestamptz not null default now(),
  unique (batch_id, student_id, date)
);

create table public.assessments (
  id          uuid primary key default gen_random_uuid(),
  batch_id    uuid not null references public.batches(id) on delete cascade,
  title       text not null check (char_length(title) between 1 and 80),
  max_marks   numeric(6,2) not null check (max_marks > 0),
  held_on     date not null default current_date,
  created_by  uuid references public.profiles(id) on delete set null,
  created_at  timestamptz not null default now()
);

create table public.marks (
  id                 uuid primary key default gen_random_uuid(),
  assessment_id      uuid not null references public.assessments(id) on delete cascade,
  student_id         uuid not null references public.students(id) on delete cascade,
  score              numeric(6,2) check (score >= 0),
  remarks            text check (char_length(remarks) <= 300),
  answer_sheet_path  text,
  updated_by         uuid references public.profiles(id) on delete set null,
  updated_at         timestamptz not null default now(),
  unique (assessment_id, student_id)
);

create table public.parent_students (
  parent_id   uuid not null references public.profiles(id) on delete cascade,
  student_id  uuid not null references public.students(id) on delete cascade,
  primary key (parent_id, student_id)
);

-- Access keys. Only a SHA-256 hash of each key is stored, never the key.
create table public.invites (
  id           uuid primary key default gen_random_uuid(),
  code_hash    text not null unique,
  role         public.app_role not null,
  student_ids  uuid[] not null default '{}',
  label        text check (char_length(label) <= 80),
  created_by   uuid references public.profiles(id) on delete set null,
  created_at   timestamptz not null default now(),
  expires_at   timestamptz not null,
  used_by      uuid references public.profiles(id) on delete set null,
  used_at      timestamptz
);

create table public.redeem_attempts (
  user_id       uuid not null,
  attempted_at  timestamptz not null default now()
);
create index on public.redeem_attempts (user_id, attempted_at);

create table public.audit_log (
  id          bigint generated always as identity primary key,
  at          timestamptz not null default now(),
  actor       uuid,
  actor_name  text,
  table_name  text not null,
  action      text not null,
  row_id      uuid,
  old_data    jsonb,
  new_data    jsonb
);

create index on public.attendance (batch_id, date);
create index on public.attendance (student_id, date);
create index on public.marks (student_id);
create index on public.batch_students (student_id);
create index on public.parent_students (student_id);

-- ---------------------------------------------------------------------
-- Role helpers (SECURITY DEFINER so policies can read profiles safely)
-- ---------------------------------------------------------------------
create or replace function public.my_role() returns public.app_role
language sql stable security definer set search_path = public as $$
  select role from public.profiles where id = auth.uid() and active
$$;

create or replace function public.is_member() returns boolean
language sql stable security definer set search_path = public as $$
  select public.my_role() is not null
$$;

create or replace function public.is_staff() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.my_role() in ('admin', 'teacher'), false)
$$;

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.my_role() = 'admin', false)
$$;

create or replace function public.is_parent_of(sid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.my_role() = 'parent', false)
     and exists (select 1 from public.parent_students where parent_id = auth.uid() and student_id = sid)
$$;

create or replace function public.is_parent_of_text(sid text) returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.my_role() = 'parent', false)
     and exists (select 1 from public.parent_students where parent_id = auth.uid() and student_id::text = sid)
$$;

-- ---------------------------------------------------------------------
-- Integrity triggers: who-did-it stamps cannot be forged by the app
-- ---------------------------------------------------------------------
create or replace function public.stamp_attendance() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  new.marked_by := auth.uid();
  new.updated_at := now();
  if new.date > current_date + 1 then
    raise exception 'Attendance cannot be marked for a future date';
  end if;
  return new;
end $$;
create trigger trg_stamp_attendance before insert or update on public.attendance
for each row execute function public.stamp_attendance();

create or replace function public.stamp_assessment() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then new.created_by := auth.uid(); end if;
  if tg_op = 'UPDATE' then new.created_by := old.created_by; end if;
  return new;
end $$;
create trigger trg_stamp_assessment before insert or update on public.assessments
for each row execute function public.stamp_assessment();

create or replace function public.stamp_mark() returns trigger
language plpgsql security definer set search_path = public as $$
declare v_max numeric;
begin
  new.updated_by := auth.uid();
  new.updated_at := now();
  select max_marks into v_max from public.assessments where id = new.assessment_id;
  if new.score is not null and new.score > v_max then
    raise exception 'Score % is more than the maximum % for this test', new.score, v_max;
  end if;
  if new.answer_sheet_path is not null
     and split_part(new.answer_sheet_path, '/', 1) <> new.student_id::text then
    raise exception 'Answer sheet belongs to a different student';
  end if;
  return new;
end $$;
create trigger trg_stamp_mark before insert or update on public.marks
for each row execute function public.stamp_mark();

-- Audit trail of every change to sensitive data (visible to admin only)
create or replace function public.audit() returns trigger
language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
  begin
    v_id := coalesce((to_jsonb(new) ->> 'id'), (to_jsonb(old) ->> 'id'))::uuid;
  exception when others then v_id := null; end;
  insert into public.audit_log (actor, actor_name, table_name, action, row_id, old_data, new_data)
  values (auth.uid(), (select full_name from public.profiles where id = auth.uid()),
          tg_table_name, tg_op, v_id,
          case when tg_op <> 'INSERT' then to_jsonb(old) end,
          case when tg_op <> 'DELETE' then to_jsonb(new) end);
  return coalesce(new, old);
end $$;

create trigger trg_audit_attendance after insert or update or delete on public.attendance for each row execute function public.audit();
create trigger trg_audit_marks after insert or update or delete on public.marks for each row execute function public.audit();
create trigger trg_audit_assessments after insert or update or delete on public.assessments for each row execute function public.audit();
create trigger trg_audit_students after insert or update or delete on public.students for each row execute function public.audit();
create trigger trg_audit_profiles after insert or update or delete on public.profiles for each row execute function public.audit();
create trigger trg_audit_parent_students after insert or update or delete on public.parent_students for each row execute function public.audit();

-- ---------------------------------------------------------------------
-- Row Level Security — every table is locked by default
-- ---------------------------------------------------------------------
alter table public.profiles        enable row level security;
alter table public.classes         enable row level security;
alter table public.subjects        enable row level security;
alter table public.students        enable row level security;
alter table public.batches         enable row level security;
alter table public.batch_students  enable row level security;
alter table public.schedule_slots  enable row level security;
alter table public.attendance      enable row level security;
alter table public.assessments     enable row level security;
alter table public.marks           enable row level security;
alter table public.parent_students enable row level security;
alter table public.invites         enable row level security;
alter table public.redeem_attempts enable row level security;
alter table public.audit_log       enable row level security;

-- profiles: you see yourself; staff see everyone. Nobody can edit roles directly.
create policy profiles_read on public.profiles for select to authenticated
  using (id = auth.uid() or public.is_staff());

-- reference data: any approved member can read, only admin writes
create policy classes_read  on public.classes  for select to authenticated using (public.is_member());
create policy classes_write on public.classes  for all    to authenticated using (public.is_admin()) with check (public.is_admin());
create policy subjects_read  on public.subjects for select to authenticated using (public.is_member());
create policy subjects_write on public.subjects for all    to authenticated using (public.is_admin()) with check (public.is_admin());
create policy batches_read  on public.batches  for select to authenticated using (public.is_member());
create policy batches_write on public.batches  for all    to authenticated using (public.is_admin()) with check (public.is_admin());
create policy slots_read  on public.schedule_slots for select to authenticated using (public.is_member());
create policy slots_write on public.schedule_slots for all    to authenticated using (public.is_admin()) with check (public.is_admin());

-- students: staff see all; a parent sees only their own children
create policy students_read  on public.students for select to authenticated
  using (public.is_staff() or public.is_parent_of(id));
create policy students_write on public.students for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

create policy batch_students_read  on public.batch_students for select to authenticated
  using (public.is_staff() or public.is_parent_of(student_id));
create policy batch_students_write on public.batch_students for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- attendance: staff mark; parents read their child only
create policy attendance_read   on public.attendance for select to authenticated
  using (public.is_staff() or public.is_parent_of(student_id));
create policy attendance_insert on public.attendance for insert to authenticated with check (public.is_staff());
create policy attendance_update on public.attendance for update to authenticated using (public.is_staff()) with check (public.is_staff());
create policy attendance_delete on public.attendance for delete to authenticated using (public.is_admin());

-- tests & marks: staff write; parents read their child only
create policy assessments_read   on public.assessments for select to authenticated using (public.is_member());
create policy assessments_insert on public.assessments for insert to authenticated with check (public.is_staff());
create policy assessments_update on public.assessments for update to authenticated using (public.is_staff()) with check (public.is_staff());
create policy assessments_delete on public.assessments for delete to authenticated using (public.is_admin());

create policy marks_read   on public.marks for select to authenticated
  using (public.is_staff() or public.is_parent_of(student_id));
create policy marks_insert on public.marks for insert to authenticated with check (public.is_staff());
create policy marks_update on public.marks for update to authenticated using (public.is_staff()) with check (public.is_staff());
create policy marks_delete on public.marks for delete to authenticated using (public.is_admin());

create policy parent_students_read  on public.parent_students for select to authenticated
  using (parent_id = auth.uid() or public.is_staff());
create policy parent_students_write on public.parent_students for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

create policy invites_read on public.invites for select to authenticated using (public.is_admin());
create policy audit_read   on public.audit_log for select to authenticated using (public.is_admin());
-- invites (write) and redeem_attempts: no policies → reachable only through the functions below.

-- Signed-in users reach tables only through the policies above.
grant usage on schema public to authenticated;
grant select, insert, update, delete on all tables in schema public to authenticated;
grant usage, select on all sequences in schema public to authenticated;

-- Remove all anonymous (not-signed-in) access to tables
revoke all on all tables in schema public from anon;

-- ---------------------------------------------------------------------
-- Access keys
-- ---------------------------------------------------------------------
create or replace function public._make_invite(p_role public.app_role, p_label text,
  p_student_ids uuid[], p_valid_hours int) returns text
language plpgsql security definer set search_path = public, extensions as $$
declare
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';  -- no 0/O/1/I to avoid confusion
  b bytea := gen_random_bytes(12);
  v_code text := '';
  i int;
begin
  for i in 0..11 loop
    v_code := v_code || substr(alphabet, (get_byte(b, i) % 32) + 1, 1);
  end loop;
  insert into public.invites (code_hash, role, student_ids, label, created_by, expires_at)
  values (encode(digest(v_code, 'sha256'), 'hex'), p_role, coalesce(p_student_ids, '{}'),
          p_label, auth.uid(), now() + make_interval(hours => p_valid_hours));
  return substr(v_code, 1, 4) || '-' || substr(v_code, 5, 4) || '-' || substr(v_code, 9, 4);
end $$;
revoke all on function public._make_invite(public.app_role, text, uuid[], int) from public, anon, authenticated;

create or replace function public.create_invite(p_role public.app_role, p_label text,
  p_student_ids uuid[] default '{}', p_valid_hours int default 48) returns text
language plpgsql security definer set search_path = public as $$
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
  return public._make_invite(p_role, p_label, p_student_ids, p_valid_hours);
end $$;
revoke all on function public.create_invite(public.app_role, text, uuid[], int) from public, anon;
grant execute on function public.create_invite(public.app_role, text, uuid[], int) to authenticated;

-- Run `select public.admin_bootstrap_code();` in the SQL editor to get the first admin key.
-- Not callable from the app at all.
create or replace function public.admin_bootstrap_code() returns text
language plpgsql security definer set search_path = public as $$
begin
  return public._make_invite('admin', 'Administrator (bootstrap)', '{}', 24);
end $$;
revoke all on function public.admin_bootstrap_code() from public, anon, authenticated;

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
  end if;

  update public.invites set used_by = v_uid, used_at = now() where id = v_inv.id;
  return jsonb_build_object('ok', true, 'role', v_inv.role);
end $$;
revoke all on function public.redeem_invite(text, text) from public, anon;
grant execute on function public.redeem_invite(text, text) to authenticated;

create or replace function public.revoke_invite(p_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'Not allowed' using errcode = '42501'; end if;
  delete from public.invites where id = p_id and used_at is null;
end $$;
revoke all on function public.revoke_invite(uuid) from public, anon;
grant execute on function public.revoke_invite(uuid) to authenticated;

create or replace function public.set_user_active(p_user uuid, p_active boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'Not allowed' using errcode = '42501'; end if;
  if p_user = auth.uid() then raise exception 'You cannot block your own account'; end if;
  update public.profiles set active = p_active where id = p_user;
end $$;
revoke all on function public.set_user_active(uuid, boolean) from public, anon;
grant execute on function public.set_user_active(uuid, boolean) to authenticated;

-- ---------------------------------------------------------------------
-- Answer-sheet storage (private bucket). Files live at <student_id>/<test_id>/<file>
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('answer-sheets', 'answer-sheets', false, 10485760,
        array['image/jpeg', 'image/png', 'image/webp', 'application/pdf'])
on conflict (id) do nothing;

create policy answer_sheets_read on storage.objects for select to authenticated
  using (bucket_id = 'answer-sheets'
         and (public.is_staff() or public.is_parent_of_text((storage.foldername(name))[1])));
create policy answer_sheets_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'answer-sheets' and public.is_staff());
create policy answer_sheets_update on storage.objects for update to authenticated
  using (bucket_id = 'answer-sheets' and public.is_staff())
  with check (bucket_id = 'answer-sheets' and public.is_staff());
create policy answer_sheets_delete on storage.objects for delete to authenticated
  using (bucket_id = 'answer-sheets' and public.is_admin());
