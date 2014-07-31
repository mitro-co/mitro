lib_LTLIBRARIES = libkeyczarjson.la

AM_CXXFLAGS=-std=gnu++11

libkeyczarjson_la_SOURCES = \
  json_keyset_reader.cc \
  json_keyset_reader.h \
  keyczar_session.cc \
  keyczar_session.h \
  mitrokey.cc \
  mitrokey.h

libkeyczarjson_la_LDFLAGS = -lkeyczar

include_keyczar_jsondir = $(includedir)/keyczar_json
include_keyczar_json_HEADERS = \
  json_keyset_reader.h \
  keyczar_session.h \
  mitrokey.h

check_PROGRAMS = json_keyset_reader_test keyczar_session_test mitrokey_test

keyczar_json_test_libs = \
  libkeyczarjson.la \
  ../third_party/lib/libgtest.a \
  ../third_party/lib/libgtest_main.a \
  -lcrypto \
  -lz

json_keyset_reader_test_LDADD = $(keyczar_json_test_libs)
json_keyset_reader_test_SOURCES = json_keyset_reader_test.cc

keyczar_session_test_LDADD = $(keyczar_json_test_libs)
keyczar_session_test_SOURCES = keyczar_session_test.cc

mitrokey_test_LDADD = $(keyczar_json_test_libs)
mitrokey_test_SOURCES = mitrokey_test.cc

TESTS = json_keyset_reader_test keyczar_session_test mitrokey_test
