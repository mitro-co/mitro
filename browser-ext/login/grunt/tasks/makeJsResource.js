module.exports = function(grunt) {
  var _ = require("lodash");

  var doc = "Converts a file to into a js variable, allowing us to avoid " +
            "iframe restrictions";
  grunt.registerMultiTask("makeJsResource", doc, function() {
    _.forEach(this.files, function(f) {
      var blob = _(f.src).transform(function(contents, path) {
        // read each source file into contents
        contents[path] = grunt.file.read(path);
      }, {}).transform(function(contents, blob, path) {
        // munge the path
        contents["__" + path.replace(/[^A-Za-z0-9_]/g, "_") + "_"] = blob;
      }).mapValues(function(blob) {
        // quote the blob and escape characters
        return JSON.stringify(blob);
      }).reduce(function(result, jsonBlob, variable) {
        // write as a variable assignment
        return result + "var " + variable + " = " + jsonBlob + ";\n";
      }, "");

      // write to disk
      grunt.file.write(f.dest, blob);
    });
  });
};
