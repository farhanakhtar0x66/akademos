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
cur.execute(open(sys.argv[1]).read().replace("create extension if not exists pgcrypto with schema extensions;", ""))
cur.execute("grant execute on all functions in schema extensions to anon, authenticated")
print("schema applied OK")

U = {k: str(uuid.uuid4()) for k in ["admin","teacher","parent","parent2","student"]}
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
check("teacher can upload answer sheets", not str(run("teacher", "insert into storage.objects(bucket_id,name) values ('answer-sheets',%s)", (f"{st1}/x/z.jpg",))).startswith("ERR"))
check("no-key account sees no answer sheets", run("student", "select * from storage.objects") == [])

# ---- block a teacher
run("admin", "select public.set_user_active(%s,false)", (U['teacher'],))
check("blocked teacher loses access immediately", run("teacher", "select * from students") == [] and "ERR" in str(run("teacher", "insert into marks(assessment_id,student_id,score) values (%s,%s,1)", (tid,st2))))
check("admin cannot block self", "ERR" in str(run("admin", "select public.set_user_active(%s,false)", (U['admin'],))))
check("audit log recorded changes", len(run("admin", "select * from audit_log")) > 5)

fails = [n for ok, n in results if not ok]
print(f"\n{len(results)-len(fails)}/{len(results)} passed")
srv.cleanup()
sys.exit(1 if fails else 0)
