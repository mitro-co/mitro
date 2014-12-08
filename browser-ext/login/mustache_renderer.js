/*
 * *****************************************************************************
 * Copyright (c) 2012, 2013, 2014 Lectorius, Inc.
 * Authors:
 * Vijay Pandurangan (vijayp@mitro.co)
 * Evan Jones (ej@mitro.co)
 * Adam Hilss (ahilss@mitro.co)
 *
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *     You can contact the authors at inbound@mitro.co.
 * *****************************************************************************
 */

assert = require('assert');
fs = require('fs');
path = require('path');
Hogan = require('hogan.js');


var render = function (inputFile, outputFile, params) {
    fs.readFile(inputFile, 'utf8', function (error, data) {
        assert(error === null);
        var input = Hogan.compile(data);
        var output = input.render({}, params);

        fs.writeFile(outputFile, output, function (error) {
            assert(error === null);
        });
    });
};

var loadTemplates = function (templates, params, callback) {
    if (templates.length === 0) {
        callback(params);
    } else {
        var templatePath = templates.shift();
        fs.readFile(templatePath, 'utf8', function (error, data) {
            assert(error === null);
            var key = path.basename(templatePath, path.extname(templatePath));
            params[key] = data;
            loadTemplates(templates, params, callback);
        });
    }
};

if (process.argv.length !== 4) {
    console.log('Usage: ' + process.argv[1] + ' input_file output_file');
    process.exit(code=1);
}

var TEMPLATE_DIR = __dirname + '/frontend/base_templates/';
var templateFiles = fs.readdirSync(TEMPLATE_DIR);
for (var i = 0; i < templateFiles.length; i++) {
    templateFiles[i] = TEMPLATE_DIR + templateFiles[i];
}

var inputFile = process.argv[2];
var outputFile = process.argv[3];

loadTemplates(templateFiles, {}, function (params) {
    render(inputFile, outputFile, params);
});
