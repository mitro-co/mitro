struct Boolean {
  1:optional bool value;
}

struct Number {
  1:optional byte i8_value;
  2:optional i16 i16_value;
  3:optional i32 i32_value;
  4:optional i64 i64_value;
  5:optional double double_value;
}

struct BinaryData {
  1:binary data;
}

struct Date {
  1:optional i32 day,
  2:required i32 month,
  3:i32 year
}

struct String {
  1:string s
}

struct Person {
  1:optional string name,
  2:double weight,
  3:Date birthday,
  4:map<string, i32> dict,
  5:list<String> objlist,
  6:map<string, String> objdict,
  7:set<string> params,
  8:binary b;
}

