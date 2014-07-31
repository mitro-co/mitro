lib_LTLIBRARIES = libchromiumbase.la libchromiumjson.la

AM_CXXFLAGS=-std=gnu++11
AM_OBJCXXFLAGS=-std=gnu++11

libchromiumbase_la_SOURCES = \
  containers/hash_tables.h \
  files/file_path.cc \
  files/file_path.h \
  files/file_path_constants.cc \
  memory/raw_scoped_refptr_mismatch_checker.h \
  memory/ref_counted.cc \
  memory/ref_counted.h \
  memory/scoped_policy.h \
  memory/scoped_ptr.h \
  memory/weak_ptr.cc \
  memory/weak_ptr.h \
  posix/eintr_wrapper.h \
  strings/string16.cc \
  strings/string16.h \
  strings/string_number_conversions.cc \
  strings/string_number_conversions.h \
  strings/string_piece.cc \
  strings/string_piece.h \
  strings/string_split.cc \
  strings/string_split.h \
  strings/string_util.cc \
  strings/string_util.h \
  strings/string_util_constants.cc \
  strings/stringprintf.cc \
  strings/stringprintf.h \
  strings/sys_string_conversions.h \
  strings/utf_string_conversions.cc \
  strings/utf_string_conversions.h \
  strings/utf_string_conversion_utils.cc \
  strings/utf_string_conversion_utils.h \
  third_party/dmg_fp/dmg_fp.h \
  third_party/dmg_fp/dtoa.cc \
  third_party/dmg_fp/g_fmt.cc \
  third_party/icu/icu_utf.cc \
  third_party/icu/icu_utf.h \
  time/time.cc \
  time/time.h \
  time/time_posix.cc \
  atomicops.h \
  atomicops_internals_atomicword_compat.h \
  atomic_ref_count.h \
  base_export.h \
  basictypes.h \
  bind.h \
  bind_helpers.cc \
  bind_helpers.h \
  bind_internal.h \
  callback.h \
  callback_forward.h \
  callback_internal.cc \
  callback_internal.h \
  command_line.cc \
  command_line.h \
  compiler_specific.h \
  float_util.h \
  gtest_prod_util.h \
  logging.cc \
  logging.h \
  move.h \
  pickle.cc \
  pickle.h \
  port.h \
  safe_strerror_posix.cc \
  safe_strerror_posix.h \
  scoped_clear_errno.h \
  stl_util.h \
  template_util.h \
  tuple.h \
  values.cc \
  values.h \
  vlog.cc \
  vlog.h

libchromiumbase_la_SOURCES += \
  strings/sys_string_conversions_mac.mm \
  mac/bundle_locations.h \
  mac/bundle_locations.mm \
  mac/foundation_util.h \
  mac/foundation_util.mm \
  mac/mac_logging.cc \
  mac/mac_logging.h \
  mac/mac_util.h \
  mac/mac_util.mm \
  mac/scoped_cftyperef.h \
  time/time_mac.cc \
  atomicops_internals_mac.h

libchromiumbase_la_LDFLAGS = -framework CoreFoundation -framework Foundation

libchromiumjson_la_SOURCES = \
  json/json_parser.cc \
  json/json_parser.h \
  json/json_reader.cc \
  json/json_reader.h \
  json/json_writer.cc \
  json/json_writer.h \
  json/string_escape.cc \
  json/string_escape.h

libchromiumjson_la_LDFLAGS = libchromiumbase.la

include_basedir = $(includedir)/base
nobase_include_base_HEADERS = \
  containers/hash_tables.h \
  files/file_path.h \
  memory/raw_scoped_refptr_mismatch_checker.h \
  memory/ref_counted.h \
  memory/scoped_policy.h \
  memory/scoped_ptr.h \
  memory/weak_ptr.h \
  posix/eintr_wrapper.h \
  strings/string16.h \
  strings/string_number_conversions.h \
  strings/string_piece.h \
  strings/string_split.h \
  strings/string_util.h \
  strings/stringprintf.h \
  strings/sys_string_conversions.h \
  strings/utf_string_conversions.h \
  strings/utf_string_conversion_utils.h \
  third_party/dmg_fp/dmg_fp.h \
  third_party/icu/icu_utf.h \
  time/time.h \
  atomicops.h \
  atomicops_internals_atomicword_compat.h \
  atomic_ref_count.h \
  base_export.h \
  basictypes.h \
  bind.h \
  bind_helpers.h \
  bind_internal.h \
  callback.h \
  callback_forward.h \
  callback_internal.h \
  command_line.h \
  compiler_specific.h \
  float_util.h \
  gtest_prod_util.h \
  logging.h \
  move.h \
  pickle.h \
  port.h \
  safe_strerror_posix.h \
  scoped_clear_errno.h \
  stl_util.h \
  template_util.h \
  tuple.h \
  values.h \
  vlog.h

nobase_include_base_HEADERS += \
  mac/bundle_locations.h \
  mac/foundation_util.h \
  mac/mac_logging.h \
  mac/mac_util.h \
  mac/scoped_cftyperef.h \
  atomicops_internals_mac.h

include_base_jsondir = $(include_basedir)/json
include_base_json_HEADERS = \
  json/json_parser.h \
  json/json_reader.h \
  json/json_writer.h \
  json/string_escape.h
