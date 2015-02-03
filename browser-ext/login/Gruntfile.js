// TODO:
// If merged, we can get rid of:
// - various python helper scripts
// - browser-ext/third_party/firefox-addon-sdk
//
// Advantages over the old Makefile:
// - separation of configuration and logic
// - avoids over-abstraction of build directories as variables

module.exports = function(grunt) {
  // see also: grunt/{config,tasks}/*
  require("load-grunt-config")(grunt, {
    configPath: "grunt/config",

    // commonly used variables
    data: {
      pkg: require("./package.json"),
      testFiles: "**/*_{reg,}test{2,}.js",
      minifiedFiles: "**/*{.,-}min.js",
      contentFiles: "**/{content{,scriptbase,log},infobar_*}.js"
    }

  });
  // if we want to add custom (non-alias) tasks, we can add them in grunt/tasks
  grunt.loadTasks("grunt/tasks");
};
