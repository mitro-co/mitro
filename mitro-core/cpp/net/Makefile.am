lib_LTLIBRARIES = libhttpclient.la

AM_CXXFLAGS=-std=gnu++11
AM_OBJCXXFLAGS=-std=gnu++11

libhttpclient_la_SOURCES = \
  http_client.cc \
  http_client.h \
  http_client_mac.mm \
  uri.cc \
  uri.h

libhttpclient_la_LIBADD = ../base/libchromiumbase.la

include_netdir = $(includedir)/net
include_net_HEADERS = \
  http_client.h \
  uri.h

check_PROGRAMS = http_client_test

http_client_test_LDADD = \
libhttpclient.la \
../third_party/lib/libgtest.a \
../third_party/lib/libgtest_main.a

http_client_test_SOURCES = http_client_test.cc 

TESTS = http_client_test
