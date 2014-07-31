lib_LTLIBRARIES = libprotobufjson.la

libprotobufjson_la_SOURCES = \
  protobuf_json.cc \
  protobuf_json.h

libprotobufjson_la_LDFLAGS = ../base/libchromiumbase.la ../base/libchromiumjson.la -lprotobuf

include_protobufjsondir = $(includedir)/protobuf_json
include_protobufjson_HEADERS = \
  protobuf_json.h

protoc_inputs = test.proto

protoc_outputs = test.pb.cc test.pb.h

$(protoc_outputs): $(protoc_inputs)
	protoc -I$(srcdir) --cpp_out=. $^

check_PROGRAMS = protobuf_json_test

protobuf_json_test_LDADD = -lprotobuf libprotobufjson.la -lgtest -lgtest_main

protobuf_json_test_SOURCES = \
  $(protoc_outputs) \
  protobuf_json_test.cc

TESTS = protobuf_json_test
