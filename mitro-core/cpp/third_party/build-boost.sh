# Script for building boost for ios
#
# Usage: build-boost src_dir dst_dir
#

set -e

if [[ -z "$1" ]] || [[ -z "$2" ]]; then
  echo "Usage: build-boost src_dir dst_dir"
  exit 1
fi

SRC_DIR="$1"
DST_DIR="$2"

BOOST_TAR_GZ="boost_1_54_0.tar.gz"
BOOST_SHA1="069501636097d3f40ddfd996d29748bb23591c53"
BOOST_URL="http://sourceforge.net/projects/boost/files/boost/1.54.0/${BOOST_TAR_GZ}"
BOOST_SRC_DIR="${SRC_DIR}/${BOOST_TAR_GZ%.tar.gz}"

${TOOLS_DIR}/download_and_extract.sh ${BOOST_URL} ${BOOST_SHA1} ${SRC_DIR}

mkdir -p ${DST_DIR}/include
ln -s -f ${BOOST_SRC_DIR}/boost ${DST_DIR}/include
