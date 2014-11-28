#!/bin/bash
#
# *****************************************************************************
# Authors:
# Marco De Nadai (http://www.marcodena.it)
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

set -e

ABSOLUTE_BUILD=`pwd`/../third_party

git clone "https://github.com/mozilla/addon-sdk.git" "$ABSOLUTE_BUILD/firefox-addon-sdk"
#TODO: git checkout 1.16

git clone "https://github.com/twitter/hogan.js.git" "$ABSOLUTE_BUILD/hogan.js"
git clone "https://github.com/google/closure-library.git" "$ABSOLUTE_BUILD/closure-library"

echo "All third part modules installed"
