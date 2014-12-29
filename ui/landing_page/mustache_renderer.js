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
    if (templates.length == 0) {
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
}

if (process.argv.length !== 4) {
    console.log('Usage: ' + process.argv[1] + ' input_file output_file');
    process.exit(code=1);
}

var TEMPLATE_DIR = __dirname + '/base_templates/';
var templateFiles = fs.readdirSync(TEMPLATE_DIR);
for (var i = 0; i < templateFiles.length; i++) {
    templateFiles[i] = TEMPLATE_DIR + templateFiles[i];
}

var inputFile = process.argv[2];
var outputFile = process.argv[3];

loadTemplates(templateFiles, {}, function (params) {
    render(inputFile, outputFile, params);
});
