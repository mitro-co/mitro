AC_INIT([mitro_api], [0.0.1])
AM_INIT_AUTOMAKE([foreign serial-tests])
AC_CONFIG_MACRO_DIR([m4])

AC_PROG_CXX
AC_PROG_OBJC
AC_PROG_OBJCXX
LT_INIT

AC_ARG_ENABLE([third_party],
    AS_HELP_STRING([--enable-third_party], [Build third-party libs]))

have_third_party=yes
if test "$enable_third_party" = "no"; then
  have_third_party="no"
fi
AM_CONDITIONAL(ENABLE_THIRD_PARTY, [test "$have_third_party" = "yes"])

AC_CONFIG_HEADERS([config.h])
AC_CONFIG_FILES([
  Makefile
  build/Makefile
  base/Makefile
  third_party/Makefile
  keyczar_json/Makefile
  mitro_api/Makefile
  net/Makefile])
AC_OUTPUT
