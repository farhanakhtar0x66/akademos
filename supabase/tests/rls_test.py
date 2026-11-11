import sys, uuid, re, pgserver, psycopg2, shutil, os
d = os.path.abspath("pgdata"); shutil.rmtree(d, ignore_errors=True)
srv = pgserver.get_server(d, cleanup_mode="stop")
conn = psycopg2.connect(srv.get_uri()); conn.autocommit = True
cur = conn.cursor()
STUB = """
create role anon nologin; create role authenticated nologin;
create schema auth; create table auth.users(id uuid primary key);
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub', true),'')::uuid $$;
create schema extensions;
create schema storage;
create table storage.buckets(id text primary key, name text, public bool, file_size_limit bigint, allowed_mime_types text[]);
create table storage.objects(id uuid default gen_random_uuid() primary key, bucket_id text, name text);
alter table storage.objects enable row level security;
create function storage.foldername(name text) returns text[] language sql as $$ select (string_to_array(name,'/'))[1:array_length(string_to_array(name,'/'),1)-1] $$;
grant usage on schema public, auth, storage, extensions to anon, authenticated;
grant all on all tables in schema storage to authenticated;
grant execute on all functions in schema auth, storage to anon, authenticated;
alter default privileges in schema public grant all on tables to anon, authenticated;
alter default privileges in schema public grant all on sequences to anon, authenticated;
alter default privileges in schema public grant execute on functions to anon, authenticated;
"""
cur.execute(STUB)
cur.execute("""create function extensions.digest(t text, alg text) returns bytea language sql as $$ select sha256(convert_to(t,'UTF8')) $$;
create function extensions.gen_random_bytes(n int) returns bytea language sql volatile as $$ select decode(substr(md5(random()::text)||md5(random()::text),1,n*2),'hex') $$;""")
for f in [x for x in sys.argv[1:] if "004" not in x]:
    cur.execute(open(f).read().replace("create extension if not exists pgcrypto with schema extensions;", ""))
cur.execute("grant execute on all functions in schema extensions to anon, authenticated")
print("schema applied OK")

U = {k: str(uuid.uuid4()) for k in ["admin","teacher","teacher2","parent","parent2","student"]}
for v in U.values(): cur.execute("insert into auth.users values (%s)", (v,))

results = []
def run(who, sql, args=None, expect="ok"):
    c = conn.cursor()
    try:
        c.execute("begin")
        if who == "anon": c.execute("set local role anon")
        elif who != "super":
            c.execute("set local role authenticated"); c.execute("select set_config('request.jwt.claim.sub', %s, true)", (U[who],))
        c.execute(sql, args)
        out = c.fetchall() if c.description else c.rowcount
        c.execute("commit"); return out
    except Exception as e:
        c.execute("rollback"); return "ERR: " + str(e).split("\n")[0]

def check(name, cond, detail=""):
    results.append((cond, name)); print(("PASS " if cond else "FAIL ") + name + (f"  -> {detail}" if not cond else ""))

def redeem(who, code, name="Some Name"): return run(who, "select public.redeem_invite(%s,%s)", (code, name))[0][0]

# ---- bootstrap admin
r = run("student", "select public.admin_bootstrap_code()")
check("app users cannot call admin_bootstrap_code", isinstance(r, str) and "permission denied" in r, r)
code = run("super", "select public.admin_bootstrap_code()")[0][0]
check("admin redeems bootstrap key", redeem("admin", code, "Dad")["ok"] is True)
check("bootstrap key cannot be reused", redeem("student", code)["ok"] is False)

# ---- admin creates data
cid = run("admin", "insert into classes(name) values ('Class 10') returning id")[0][0]
sid = run("admin", "insert into subjects(name) values ('English') returning id")[0][0]
st1 = run("admin", "insert into students(full_name,class_id) values ('Riya Sharma',%s) returning id", (cid,))[0][0]
st2 = run("admin", "insert into students(full_name,class_id) values ('Aman Verma',%s) returning id", (cid,))[0][0]
bid = run("admin", "insert into batches(name,class_id,subject_id) values ('Class 10 · English',%s,%s) returning id", (cid,sid))[0][0]
run("admin", "insert into batch_students values (%s,%s),(%s,%s)", (bid,st1,bid,st2))
run("admin", "insert into schedule_slots(batch_id,weekday,start_time,end_time) values (%s,1,'16:00','17:00')", (bid,))
tid = run("admin", "insert into assessments(batch_id,title,max_marks) values (%s,'UT1',50) returning id", (bid,))[0][0]
check("admin set up classes/students/batches", all(isinstance(x, str) and not x.startswith("ERR") for x in [cid,sid,st1,st2,bid,tid]))

tcode = run("admin", "select public.create_invite('teacher','Mrs Rao','{}',48)")[0][0]
pcode = run("admin", "select public.create_invite('parent','Riya mum',%s::uuid[],48)", ([st1],))[0][0]
p2code = run("admin", "select public.create_invite('parent','Aman dad',%s::uuid[],48)", ([st2],))[0][0]
check("admin cannot mint admin keys from app", "ERR" in str(run("admin", "select public.create_invite('admin','x','{}',48)")))
check("teacher redeems key", redeem("teacher", tcode, "Mrs Rao")["ok"] is True)
check("parent redeems key", redeem("parent", pcode, "Riya Mum")["ok"] is True)
check("parent2 redeems key", redeem("parent2", p2code, "Aman Dad")["ok"] is True)
check("used teacher key can't be reused by student", redeem("student", tcode)["ok"] is False)
V3 = any("003" in f for f in sys.argv[1:])
t2code = run("admin", "select public.create_invite('teacher','Mr Mehta','{}',48)")[0][0]
check("teacher2 redeems key", redeem("teacher2", t2code, "Mr Mehta")["ok"] is True)
run("admin", "update batches set teacher_id=%s where id=%s", (U['teacher'], bid))

# ---- student account (signed up, no key)
check("no-key account sees no students", run("student", "select * from students") == [])
check("no-key account sees no marks", run("student", "select * from marks") == [])
check("no-key account cannot insert marks", "ERR" in str(run("student", "insert into marks(assessment_id,student_id,score) values (%s,%s,50)", (tid,st1))))
check("no-key account cannot make itself admin", "ERR" in str(run("student", "insert into profiles(id,full_name,role) values (%s,'Hacker','admin')", (U['student'],))))
check("no-key account cannot create keys", "ERR" in str(run("student", "select public.create_invite('teacher','x','{}',48)")))
check("no-key account cannot read invites", run("student", "select * from invites") == [])
check("no-key account cannot write attendance", "ERR" in str(run("student", "insert into attendance(batch_id,student_id,date,status) values (%s,%s,current_date,'present')", (bid,st1))))
for i in range(5): redeem("student", "AAAA-BBBB-CCCC")
r = redeem("student", "AAAA-BBBB-CCCC")
check("wrong-key guessing is rate limited", "Too many" in (r.get("error") or ""), r)
check("rate limit holds even for a valid key", redeem("student", p2code)["ok"] is False)

# ---- anon
check("anonymous cannot read students", "ERR" in str(run("anon", "select * from students")))
check("anonymous cannot redeem", "ERR" in str(run("anon", "select public.redeem_invite('x','y')")))

# ---- teacher
r = run("teacher", "insert into attendance(batch_id,student_id,date,status,marked_by) values (%s,%s,current_date,'present',%s) returning marked_by", (bid,st1,U['admin']))
check("teacher marks attendance; marked_by can't be forged", r != [] and not isinstance(r,str) and str(r[0][0]) == U['teacher'], r)
run("teacher", "insert into attendance(batch_id,student_id,date,status) values (%s,%s,current_date,'absent')", (bid,st2))
check("teacher enters marks", not str(run("teacher", "insert into marks(assessment_id,student_id,score) values (%s,%s,42)", (tid,st1))).startswith("ERR"))
check("score above max is rejected", "more than the maximum" in str(run("teacher", "update marks set score=51 where student_id=%s", (st1,))))
check("answer sheet path must match student", "different student" in str(run("teacher", "update marks set answer_sheet_path=%s where student_id=%s", (f"{st2}/x/y.jpg", st1))))
check("teacher cannot delete attendance", run("teacher", "delete from attendance") == 0)
check("teacher cannot create keys", "ERR" in str(run("teacher", "select public.create_invite('parent','x',%s::uuid[],48)", ([st1],))))
check("teacher cannot edit students", run("teacher", "update students set full_name='X'") == 0)
check("teacher cannot change own role", run("teacher", "update profiles set role='admin' where id=%s", (U['teacher'],)) == 0)
check("teacher cannot read audit log", run("teacher", "select * from audit_log") == [])
check("teacher cannot use parent key to change role", redeem("teacher", run("admin", "select public.create_invite('parent','x',%s::uuid[],48)", ([st2],))[0][0])["ok"] is False)
check("future-dated attendance blocked", "future" in str(run("teacher", "insert into attendance(batch_id,student_id,date,status) values (%s,%s,current_date+5,'present')", (bid,st1))))

# ---- parents
rows = run("parent", "select id from students")
check("parent sees only own child", [str(x[0]) for x in rows] == [str(st1)], rows)
check("parent sees only own child's attendance", all(str(x[0]) == str(st1) for x in run("parent", "select student_id from attendance")) and len(run("parent", "select student_id from attendance")) == 1)
check("parent sees own child's marks", len(run("parent", "select * from marks")) == 1)
check("parent2 cannot see Riya's marks", run("parent2", "select * from marks") == [])
check("parent cannot change marks", run("parent", "update marks set score=50") == 0 or "ERR" in str(run("parent", "update marks set score=50")))
check("parent cannot insert marks", "ERR" in str(run("parent", "insert into marks(assessment_id,student_id,score) values (%s,%s,50)", (tid,st2))))
check("parent cannot change attendance", run("parent", "update attendance set status='present'") == 0)
check("parent cannot link another child", "ERR" in str(run("parent", "insert into parent_students values (%s,%s)", (U['parent'],st2))))
check("parent cannot see other parents' profiles", len(run("parent", "select * from profiles")) == 1)

# ---- storage
run("super", "insert into storage.objects(bucket_id,name) values ('answer-sheets',%s),('answer-sheets',%s)", (f"{st1}/{tid}/a.jpg", f"{st2}/{tid}/b.jpg"))
r = run("parent", "select name from storage.objects")
check("parent can open only own child's answer sheet", len(r) == 1 and r[0][0].startswith(str(st1)), r)
check("parent cannot upload answer sheets", "ERR" in str(run("parent", "insert into storage.objects(bucket_id,name) values ('answer-sheets',%s)", (f"{st1}/x/z.jpg",))))
check("teacher can upload answer sheets", not str(run("teacher", "insert into storage.objects(bucket_id,name) values ('answer-sheets',%s)", (f"{st1}/{tid}/z.jpg",))).startswith("ERR"))
check("no-key account sees no answer sheets", run("student", "select * from storage.objects") == [])

# ---- money (only if update 002 was applied)
if len(sys.argv) > 2:
    check("admin sets a monthly fee", not str(run("admin", "insert into student_fees(student_id,monthly_fee,due_day) values (%s,2000,10),(%s,1800,5)", (st1,st2))).startswith("ERR"))
    r = run("admin", "insert into fee_payments(student_id,for_month,amount,method) values (%s,date_trunc('month',current_date)::date,2000,'upi') returning recorded_by", (st1,))
    check("admin records a fee payment; recorded_by stamped", not isinstance(r,str) and str(r[0][0]) == U['admin'], r)
    run("admin", "insert into fee_payments(student_id,for_month,amount) values (%s,date_trunc('month',current_date)::date,900)", (st2,))
    check("fee month must be the 1st", "ERR" in str(run("admin", "insert into fee_payments(student_id,for_month,amount) values (%s,'2026-11-05',10)", (st1,))))
    check("negative fee payment rejected", "ERR" in str(run("admin", "insert into fee_payments(student_id,for_month,amount) values (%s,date_trunc('month',current_date)::date,-5)", (st1,))))
    pid = run("admin", "insert into payees(name,role_label,monthly_salary,pay_day) values ('Meera Rao','English teacher',18000,1) returning id")[0][0]
    check("admin records a salary", not str(run("admin", "insert into salary_payments(payee_id,for_month,amount) values (%s,date_trunc('month',current_date)::date,18000)", (pid,))).startswith("ERR"))
    check("parent sees own child's fee only", [str(x[0]) for x in run("parent", "select student_id from student_fees")] == [str(st1)])
    check("parent sees own child's payments only", len(run("parent", "select * from fee_payments")) == 1 and run("parent2", "select amount from fee_payments")[0][0] == 900)
    check("parent cannot record a payment", "ERR" in str(run("parent", "insert into fee_payments(student_id,for_month,amount) values (%s,date_trunc('month',current_date)::date,5000)", (st1,))))
    check("parent cannot change their fee", run("parent", "update student_fees set monthly_fee=1") == 0)
    check("parent cannot delete a payment record", run("parent", "delete from fee_payments") == 0)
    check("parent cannot see salaries", run("parent", "select * from salary_payments") == [] and run("parent", "select * from payees") == [])
    check("teacher cannot see any fees", run("teacher", "select * from student_fees") == [] and run("teacher", "select * from fee_payments") == [])
    check("teacher cannot see salaries", run("teacher", "select * from payees") == [] and run("teacher", "select * from salary_payments") == [])
    check("teacher cannot give self a raise", "ERR" in str(run("teacher", "insert into payees(name,monthly_salary) values ('Me',999999)")) and run("teacher", "update payees set monthly_salary=99999") == 0)
    check("teacher cannot record fees", "ERR" in str(run("teacher", "insert into fee_payments(student_id,for_month,amount) values (%s,date_trunc('month',current_date)::date,1)", (st1,))))
    check("no-key account sees no money", run("student", "select * from fee_payments") == [] and run("student", "select * from payees") == [])
    check("anonymous cannot read fees", "ERR" in str(run("anon", "select * from fee_payments")))
    check("money changes are in the audit log", len(run("admin", "select * from audit_log where table_name in ('fee_payments','salary_payments','student_fees','payees')")) >= 4)

# ---- update 003: teachers see only their own batches; check-ins; "I've paid"
if V3:
    sid2 = run("admin", "insert into subjects(name) values ('Science') returning id")[0][0]
    st3 = run("admin", "insert into students(full_name,class_id) values ('Zoya Khan',%s) returning id", (cid,))[0][0]
    bid2 = run("admin", "insert into batches(name,class_id,subject_id,teacher_id) values ('Class 10 · Science',%s,%s,%s) returning id", (cid,sid2,U['teacher2']))[0][0]
    run("admin", "insert into batch_students values (%s,%s),(%s,%s)", (bid2,st1,bid2,st3))
    tid2 = run("admin", "insert into assessments(batch_id,title,max_marks) values (%s,'Sci quiz',25) returning id", (bid2,))[0][0]
    run("teacher2", "insert into attendance(batch_id,student_id,date,status) values (%s,%s,current_date,'present')", (bid2,st3))
    check("teacher sees only own batches", [str(x[0]) for x in run("teacher", "select id from batches")] == [str(bid)])
    check("teacher sees only students they teach", sorted(str(x[0]) for x in run("teacher", "select id from students")) == sorted([str(st1), str(st2)]))
    check("teacher2 sees only their students", sorted(str(x[0]) for x in run("teacher2", "select id from students")) == sorted([str(st1), str(st3)]))
    check("teacher cannot see another teacher's attendance", all(str(x[0]) == str(bid) for x in run("teacher", "select batch_id from attendance")))
    check("teacher cannot mark another teacher's batch", "ERR" in str(run("teacher", "insert into attendance(batch_id,student_id,date,status) values (%s,%s,current_date,'present')", (bid2,st1))))
    check("teacher cannot mark a student who isn't in the batch", "ERR" in str(run("teacher2", "insert into attendance(batch_id,student_id,date,status) values (%s,%s,current_date,'present')", (bid2,st2))))
    check("teacher cannot edit another teacher's attendance", run("teacher", "update attendance set status='absent' where batch_id=%s", (bid2,)) == 0)
    check("teacher cannot see another teacher's tests", all(str(x[0]) != str(tid2) for x in run("teacher", "select id from assessments")))
    check("teacher cannot add a test to another teacher's batch", "ERR" in str(run("teacher", "insert into assessments(batch_id,title,max_marks) values (%s,'x',10)", (bid2,))))
    check("teacher cannot enter marks for another teacher's test", "ERR" in str(run("teacher", "insert into marks(assessment_id,student_id,score) values (%s,%s,5)", (tid2,st1))))
    check("teacher2 cannot see teacher's marks", run("teacher2", "select * from marks where assessment_id=%s", (tid,)) == [])
    check("marks only for students in the test's batch", "ERR" in str(run("teacher2", "insert into marks(assessment_id,student_id,score) values (%s,%s,5)", (tid2,st2))))
    check("teacher2 cannot open teacher's answer sheets", run("teacher2", "select * from storage.objects where name like %s", (f"%/{tid}/%",)) == [])
    check("teacher2 cannot upload to teacher's test", "ERR" in str(run("teacher2", "insert into storage.objects(bucket_id,name) values ('answer-sheets',%s)", (f"{st1}/{tid}/q.jpg",))))
    check("teacher no longer sees parents", all(x[0] in ('admin','teacher') for x in run("teacher", "select role from profiles")))
    check("teacher cannot see parent links", run("teacher", "select * from parent_students") == [])
    check("parent sees own child's batches only", sorted(str(x[0]) for x in run("parent2", "select id from batches")) == [str(bid)])
    check("admin still sees everything", len(run("admin", "select * from batches")) == 2 and len(run("admin", "select * from students")) == 3)

    # day plans
    check("teacher confirms today as usual", not str(run("teacher", "insert into day_plans(teacher_id,date,mode) values (%s,current_date,'usual')", (U['teacher'],))).startswith("ERR"))
    check("teacher plans a custom class today", not str(run("teacher", "insert into day_classes(teacher_id,date,batch_id,start_time,end_time) values (%s,current_date,%s,'18:00','19:00')", (U['teacher'],bid))).startswith("ERR"))
    check("teacher cannot plan another teacher's batch", "ERR" in str(run("teacher", "insert into day_classes(teacher_id,date,batch_id,start_time,end_time) values (%s,current_date,%s,'18:00','19:00')", (U['teacher'],bid2))))
    check("teacher cannot plan for someone else", "ERR" in str(run("teacher", "insert into day_plans(teacher_id,date,mode) values (%s,current_date,'off')", (U['teacher2'],))))
    check("teacher cannot rewrite the past", "ERR" in str(run("teacher", "insert into day_plans(teacher_id,date,mode) values (%s,current_date-3,'off')", (U['teacher'],))))
    check("end time must be after start", "ERR" in str(run("teacher", "insert into day_classes(teacher_id,date,batch_id,start_time,end_time) values (%s,current_date,%s,'19:00','18:00')", (U['teacher'],bid))))
    check("teacher2 cannot see teacher's plan", run("teacher2", "select * from day_plans") == [] and run("teacher2", "select * from day_classes") == [])
    check("admin sees every teacher's plan", len(run("admin", "select * from day_plans")) == 1 and len(run("admin", "select * from day_classes")) == 1)
    check("parents see no plans", run("parent", "select * from day_plans") == [] and "ERR" in str(run("parent", "insert into day_plans(teacher_id,date,mode) values (%s,current_date,'off')", (U['parent'],))))

    # "I've paid"
    r = run("parent", "insert into fee_claims(student_id,for_month,amount,method,status) values (%s,date_trunc('month',current_date)::date,1800,'upi','confirmed') returning status, claimed_by", (st1,))
    check("parent says they paid; it starts as waiting", not isinstance(r,str) and r[0][0] == 'pending' and str(r[0][1]) == U['parent'], r)
    check("only one open claim per month", "ERR" in str(run("parent", "insert into fee_claims(student_id,for_month,amount) values (%s,date_trunc('month',current_date)::date,100)", (st1,))))
    check("parent cannot claim for another child", "ERR" in str(run("parent", "insert into fee_claims(student_id,for_month,amount) values (%s,date_trunc('month',current_date)::date,100)", (st2,))))
    check("parent cannot mark own claim confirmed", run("parent", "update fee_claims set status='confirmed'") == 0)
    check("parent cannot confirm via function", "ERR" in str(run("parent", "select public.confirm_fee_claim(id) from fee_claims")))
    check("teacher cannot see claims", run("teacher", "select * from fee_claims") == [])
    check("parent2 cannot see Riya's claim", run("parent2", "select * from fee_claims") == [])
    before = run("admin", "select count(*) from fee_payments where student_id=%s", (st1,))[0][0]
    cl = run("admin", "select id from fee_claims where status='pending'")[0][0]
    run("admin", "select public.confirm_fee_claim(%s)", (cl,))
    after = run("admin", "select count(*), max(recorded_by::text) from fee_payments where student_id=%s", (st1,))[0]
    check("admin confirms; a payment is recorded", after[0] == before + 1 and after[1] == U['admin'], after)
    check("a claim can't be confirmed twice", "already handled" in str(run("admin", "select public.confirm_fee_claim(%s)", (cl,))))
    run("parent2", "insert into fee_claims(student_id,for_month,amount) values (%s,date_trunc('month',current_date)::date,900)", (st2,))
    cl2 = run("admin", "select id from fee_claims where status='pending'")[0][0]
    run("admin", "select public.reject_fee_claim(%s)", (cl2,))
    check("admin says not received; parent sees it", run("parent2", "select status from fee_claims")[0][0] == 'rejected')
    check("after a rejection the parent can claim again", not str(run("parent2", "insert into fee_claims(student_id,for_month,amount) values (%s,date_trunc('month',current_date)::date,900)", (st2,))).startswith("ERR"))

    # removing people
    check("teacher cannot remove anyone", "ERR" in str(run("teacher", "select public.remove_user(%s)", (U['parent'],))))
    check("admin cannot remove self", "ERR" in str(run("admin", "select public.remove_user(%s)", (U['admin'],))))
    run("admin", "select public.remove_user(%s)", (U['teacher2'],))
    check("removed teacher loses everything", run("teacher2", "select * from students") == [] and run("teacher2", "select * from batches") == [])

if V3:
    # teacher keys that hand over batches
    bid3 = run("admin", "insert into batches(name,class_id,subject_id) values ('Class 10 · Maths',%s,%s) returning id", (cid,sid2))[0][0]
    U['teacher3'] = str(uuid.uuid4()); run("super", "insert into auth.users values (%s)", (U['teacher3'],))
    k3 = run("admin", "select public.create_invite('teacher','Mr Alim','{}',48,%s::uuid[])", ([bid3],))[0][0]
    check("teacher key carries its batches", redeem("teacher3", k3, "Alim")["ok"] is True
          and str(run("admin", "select teacher_id from batches where id=%s", (bid3,))[0][0]) == U['teacher3'])
    check("teacher3 sees the batch from their key", [str(x[0]) for x in run("teacher3", "select id from batches")] == [str(bid3)])
    check("teacher cannot make keys that hand over batches", "ERR" in str(run("teacher", "select public.create_invite('teacher','x','{}',48,%s::uuid[])", ([bid],))))

    # pay per class
    check("admin sets a per-class rate", not str(run("admin", "insert into teacher_rates(teacher_id,per_class) values (%s,500)", (U['teacher'],))).startswith("ERR"))
    check("teacher cannot see or set rates", run("teacher", "select * from teacher_rates") == []
          and "ERR" in str(run("teacher", "insert into teacher_rates(teacher_id,per_class) values (%s,99999)", (U['teacher3'],))))
    r = run("admin", "select teacher_id, batch_id, present, total from public.classes_taken(current_date-30, current_date)")
    mine = [x for x in r if str(x[1]) == str(bid)]
    check("classes taken are counted per teacher", len(mine) == 1 and str(mine[0][0]) == U['teacher'] and mine[0][3] == 2, r)
    check("teacher cannot read the class count", "ERR" in str(run("teacher", "select * from public.classes_taken(current_date-30, current_date)")))

    # the institute's own data loads cleanly (twice, without duplicates)
    for _ in range(2): seed = run("super", open([f for f in sys.argv if "004" in f][0]).read()) if any("004" in f for f in sys.argv) else None
    if seed is not None:
        check("institute data: 36 students with fees", run("super", "select count(*) from students s join student_fees f on f.student_id=s.id join classes c on c.id=s.class_id where c.name <> 'Class 10' or s.full_name not in ('Riya Sharma','Aman Verma','Zoya Khan')")[0][0] == 36)
        seeded = "(select id from batches where name in ('Class 11 & 12 · English','Class 12 · BST & Economics','Class 12 · Accountancy','Class 12 · Maths','Class 12 · Physics','Class 11 · Maths','Class 10 · Maths & Science','Class 10 · SST & English','Class 9 · Science & SST','Class 9 · Maths & Science','Class 7 & 8 · Maths & Science','Class 7 & 8 · Other subjects','Class 6 · All subjects'))"
        check("institute data: 13 batches from the timetable", run("super", "select count(*) from batches where id in " + seeded)[0][0] == 13)
        check("institute data: 58 weekly class times on the right days", run("super", "select count(*) from schedule_slots where batch_id in " + seeded)[0][0] == 58)
        check("institute data: BST only Mon/Wed/Fri, Accounts only Tue/Thu/Sat",
              [x[0] for x in run("super", "select weekday from schedule_slots s join batches b on b.id=s.batch_id where b.name='Class 12 · BST & Economics' order by 1")] == [1,3,5]
              and [x[0] for x in run("super", "select weekday from schedule_slots s join batches b on b.id=s.batch_id where b.name='Class 12 · Accountancy' order by 1")] == [2,4,6])
        check("institute data: no Sunday classes", run("super", "select count(*) from schedule_slots where weekday = 7 and batch_id in " + seeded)[0][0] == 0)
        check("institute data: Class 11 & 12 English has 12 students", run("super", "select count(*) from batch_students bs join batches b on b.id=bs.batch_id where b.name='Class 11 & 12 · English'")[0][0] == 12)
        check("institute data: Class 7 & 8 sit together (8 students)", run("super", "select count(*) from batch_students bs join batches b on b.id=bs.batch_id where b.name='Class 7 & 8 · Maths & Science'")[0][0] == 8)
        check("institute data: teacher matched by name, unknown names left empty",
              str(run("super", "select teacher_id from batches where name='Class 12 · Maths'")[0][0]) == U['teacher3']
              and run("super", "select teacher_id from batches where name='Class 12 · Physics'")[0][0] is None)

# ---- block a teacher
run("admin", "select public.set_user_active(%s,false)", (U['teacher'],))
check("blocked teacher loses access immediately", run("teacher", "select * from students") == [] and "ERR" in str(run("teacher", "insert into marks(assessment_id,student_id,score) values (%s,%s,1)", (tid,st2))))
check("admin cannot block self", "ERR" in str(run("admin", "select public.set_user_active(%s,false)", (U['admin'],))))
check("audit log recorded changes", len(run("admin", "select * from audit_log")) > 5)

fails = [n for ok, n in results if not ok]
print(f"\n{len(results)-len(fails)}/{len(results)} passed")
srv.cleanup()
sys.exit(1 if fails else 0)
