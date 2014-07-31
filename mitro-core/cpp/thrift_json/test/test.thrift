struct Date {
  1:optional i32 day,
  2:required i32 month,
  3:i32 year
}

struct Obj {
  1:string s
}

struct Person {
  1:optional string name,
  2:double weight,
  3:Date birthday,
  4:map<string, i32> dict,
  5:list<Obj> objlist,
  6:map<string, Obj> objdict,
  7:list<string> params,
  8:list<string> l2,
  9:string s
}
