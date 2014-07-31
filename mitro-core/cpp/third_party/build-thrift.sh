# Script for building thrift for ios
#
# Usage: build-thrift src_dir dst_dir
#

set -e

if [[ -z "$1" ]] || [[ -z "$2" ]]; then
  echo "Usage: build-thrift src_dir dst_dir"
  exit 1
fi

SRC_DIR="$1"
DST_DIR="$2"

THRIFT_VERSION="0.9.1"
THRIFT_TAR_GZ="thrift-${THRIFT_VERSION}.tar.gz"
THRIFT_SHA1="dc54a54f8dc706ffddcd3e8c6cd5301c931af1cc"
THRIFT_URL="http://mirror.metrocast.net/apache/thrift/${THRIFT_VERSION}/${THRIFT_TAR_GZ}"
THRIFT_SRC_DIR="${SRC_DIR}/${THRIFT_TAR_GZ%.tar.gz}"

if [ ! -e ${THRIFT_SRC_DIR} ]; then
  "${TOOLS_DIR}/download_and_extract.sh" ${THRIFT_URL} ${THRIFT_SHA1} ${SRC_DIR}

  pushd "${THRIFT_SRC_DIR}"
  patch -p1 -N < ${PATCH_DIR}/thrift-json.patch
  popd
fi

if [ ${PLATFORM} == 'iPhoneOS' ] || [ ${PLATFORM} == 'iPhoneSimulator' ]; then
  DEVELOPER_ROOT="/Applications/Xcode.app/Contents/Developer"
  TOOLCHAIN_ROOT="${DEVELOPER_ROOT}/Toolchains/XcodeDefault.xctoolchain"
  PLATFORM_ROOT="${DEVELOPER_ROOT}/Platforms/${PLATFORM}.platform/Developer"
  SDK_ROOT="${PLATFORM_ROOT}/SDKs/${PLATFORM}${SDK_VERSION}.sdk"

  HOST_FLAG=--host=arm-iphoneos

  CC="${TOOLCHAIN_ROOT}/usr/bin/cc"
  CFLAGS="-arch ${ARCH} -isysroot ${SDK_ROOT} -miphoneos-version-min=7.0"
  CXX="${TOOLCHAIN_ROOT}/usr/bin/c++"
  CXXFLAGS="-arch ${ARCH} -isysroot ${SDK_ROOT} -miphoneos-version-min=7.0 -I\"${SRC_DIR}/mitro-api\" -I\"${DST_DIR}/include\" -stdlib=libc++"
  LDFLAGS="-arch ${ARCH} -dynamiclib -L${DST_DIR}/lib"
else
  CC=gcc
  CXX=g++
  CXXFLAGS="-I../../../../.."
  LDFLAGS="-L../../../../../base"
fi

echo "Building thrift for ${PLATFORM}${SDK_VERSION}"
pushd "${THRIFT_SRC_DIR}"

# Fix for a cross compilation bug
export ac_cv_func_malloc_0_nonnull=yes
export ac_cv_func_realloc_0_nonnull=yes
./configure ${HOST_FLAG} --prefix=${DST_DIR} --with-boost=${DST_DIR} --with-cpp=yes --with-c_glib=no --with-csharp=no --with-d=no --with-erlang=no --with-haskell=no --with-go=no --with-java=no --with-perl=no --with-php=no --with-php_extension=no --with-python=no --with-ruby=no --with-libevent=no --with-zlib=no --with-tests=no --enable-static=yes --no-create --no-recursion CC="${CC}" CFLAGS="${CFLAGS}" CXX="${CXX}" CXXFLAGS="${CXXFLAGS}" LDFLAGS="${LDFLAGS}"
# For some unknown reason, config.status is not being run during configure.
./config.status

make clean
make
make install
popd
