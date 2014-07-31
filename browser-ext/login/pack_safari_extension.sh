#
# *****************************************************************************
# Copyright (c) 2012, 2013, 2014 Lectorius, Inc.
# Authors:
# Vijay Pandurangan (vijayp@mitro.co)
# Evan Jones (ej@mitro.co)
# Adam Hilss (ahilss@mitro.co)
#
#
#     This program is free software: you can redistribute it and/or modify
#     it under the terms of the GNU General Public License as published by
#     the Free Software Foundation, either version 3 of the License, or
#     (at your option) any later version.
#
#     This program is distributed in the hope that it will be useful,
#     but WITHOUT ANY WARRANTY; without even the implied warranty of
#     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#     GNU General Public License for more details.
#
#     You should have received a copy of the GNU General Public License
#     along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
#     You can contact the authors at inbound@mitro.co.
# *****************************************************************************
#
BUILD_DIR=$1
EXTENSION_NAME=$2
CERTS_DIR=$3
cd $BUILD_DIR
xar -czf $EXTENSION_NAME.safariextz --distribution $EXTENSION_NAME.safariextension
xar --sign -f $EXTENSION_NAME.safariextz --digestinfo-to-sign digest.dat --sig-size `cat $CERTS_DIR/size.txt` --cert-loc $CERTS_DIR/cert.der --cert-loc $CERTS_DIR/cert01 --cert-loc $CERTS_DIR/cert02
openssl rsautl -sign -inkey $CERTS_DIR/key.pem -in digest.dat -out sig.dat
xar --inject-sig sig.dat -f $EXTENSION_NAME.safariextz
rm -f sig.dat digest.dat
