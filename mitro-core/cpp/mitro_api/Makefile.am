noinst_LTLIBRARIES = libmitroapi.la
#lib_LTLIBRARIES = libmitroapi.la

AM_CXXFLAGS=-std=gnu++11

mitro_api_thrift_inputs = mitro_api.thrift

mitro_api_thrift_outputs = \
  mitro_api_constants.cpp \
  mitro_api_constants.h \
  mitro_api_types.cpp \
  mitro_api_types.h

$(mitro_api_thrift_outputs): $(mitro_api_thrift_inputs)
	thrift --gen cpp -out . mitro_api.thrift

libmitroapi_la_SOURCES = \
  $(mitro_api_thrift_outputs) \
	mitro_api.cc \
  mitro_api.h

libmitroapi_la_LIBADD = \
  ../keyczar_json/libkeyczarjson.la \
  ../net/libhttpclient.la \
  -lthrift

#include_mitroapidir = $(includedir)/mitro_api
#include_mitroapi_HEADERS = \
#  mitro_api.h \
#  mitro_api_constants.h \
#  mitro_api_types.h

check_PROGRAMS = mitro_api_test

mitro_api_test_LDADD = \
libmitroapi.la \
../third_party/lib/libgtest.a \
../third_party/lib/libgtest_main.a

mitro_api_test_SOURCES = mitro_api_test.cc 

TESTS = mitro_api_test
