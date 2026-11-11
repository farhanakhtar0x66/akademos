-- =====================================================================
--  Akademos · update 002: fees, salaries and earnings
--  Run once in Supabase → SQL Editor, AFTER schema.sql.
--
--  Who can see what:
--    administrator  everything here
--    parent         only their own child's fee and payments (read only)
--    teacher        nothing (no money data at all)
-- =====================================================================

-- What each student pays per month, and the day of the month it's due.
create table public.student_fees (
  student_id   uuid primary key references public.students(id) on delete cascade,
  monthly_fee  numeric(10,2) not null check (monthly_fee >= 0),
  due_day      smallint not null default 10 check (due_day between 1 and 28),
  updated_at   timestamptz not null default now()
);

-- Every fee payment received. for_month is the 1st of the month it pays for.
create table public.fee_payments (
  id           uuid primary key default gen_random_uuid(),
  student_id   uuid not null references public.students(id) on delete cascade,
  for_month    date not null check (extract(day from for_month) = 1),
  amount       numeric(10,2) not null check (amount > 0 and amount <= 10000000),
  paid_on      date not null default current_date,
  method       text not null default 'cash' check (method in ('cash', 'upi', 'bank', 'cheque', 'other')),
  note         text check (char_length(note) <= 200),
  recorded_by  uuid references public.profiles(id) on delete set null,
  created_at   timestamptz not null default now()
);
create index on public.fee_payments (for_month);
create index on public.fee_payments (student_id);

-- People the institute pays: teachers and any other staff (they don't need an app account).
create table public.payees (
  id              uuid primary key default gen_random_uuid(),
  name            text not null check (char_length(name) between 2 and 80),
  role_label      text check (char_length(role_label) <= 40),
  monthly_salary  numeric(10,2) not null check (monthly_salary >= 0),
  pay_day         smallint not null default 1 check (pay_day between 1 and 28),
  active          boolean not null default true,
  created_at      timestamptz not null default now()
);

create table public.salary_payments (
  id           uuid primary key default gen_random_uuid(),
  payee_id     uuid not null references public.payees(id) on delete cascade,
  for_month    date not null check (extract(day from for_month) = 1),
  amount       numeric(10,2) not null check (amount > 0 and amount <= 10000000),
  paid_on      date not null default current_date,
  method       text not null default 'bank' check (method in ('cash', 'upi', 'bank', 'cheque', 'other')),
  note         text check (char_length(note) <= 200),
  recorded_by  uuid references public.profiles(id) on delete set null,
  created_at   timestamptz not null default now()
);
create index on public.salary_payments (for_month);

-- Who recorded a payment is stamped by the server and can't be faked.
create or replace function public.stamp_money() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_table_name in ('fee_payments', 'salary_payments') then
    new.recorded_by := auth.uid();
  end if;
  if tg_table_name = 'student_fees' then
    new.updated_at := now();
  end if;
  return new;
end $$;
create trigger trg_stamp_fee_payments before insert or update on public.fee_payments for each row execute function public.stamp_money();
create trigger trg_stamp_salary_payments before insert or update on public.salary_payments for each row execute function public.stamp_money();
create trigger trg_stamp_student_fees before insert or update on public.student_fees for each row execute function public.stamp_money();

-- Every change goes into the activity log.
create trigger trg_audit_fee_payments after insert or update or delete on public.fee_payments for each row execute function public.audit();
create trigger trg_audit_salary_payments after insert or update or delete on public.salary_payments for each row execute function public.audit();
create trigger trg_audit_student_fees after insert or update or delete on public.student_fees for each row execute function public.audit();
create trigger trg_audit_payees after insert or update or delete on public.payees for each row execute function public.audit();

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
alter table public.student_fees    enable row level security;
alter table public.fee_payments    enable row level security;
alter table public.payees          enable row level security;
alter table public.salary_payments enable row level security;

create policy student_fees_read  on public.student_fees for select to authenticated
  using (public.is_admin() or public.is_parent_of(student_id));
create policy student_fees_write on public.student_fees for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

create policy fee_payments_read  on public.fee_payments for select to authenticated
  using (public.is_admin() or public.is_parent_of(student_id));
create policy fee_payments_write on public.fee_payments for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

create policy payees_admin on public.payees for all to authenticated
  using (public.is_admin()) with check (public.is_admin());
create policy salary_payments_admin on public.salary_payments for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

grant select, insert, update, delete on public.student_fees, public.fee_payments, public.payees, public.salary_payments to authenticated;
revoke all on public.student_fees, public.fee_payments, public.payees, public.salary_payments from anon;
