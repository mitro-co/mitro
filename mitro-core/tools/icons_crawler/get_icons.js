// Pull out all icons for a site.



var casper = require('casper').create(
{
    loadImages: false,
    loadPlugins: false,
    verbose:true,
    debug:true,
    waitTimeout:5000,
    clientScripts: ["includes/jquery.min.js",
    'includes/cycle.js']

}
    );
casper.on('remote.message', function(message) {
//    console.log(message);
});

casper.start()
casper.userAgent('Mozilla/5.0 (Macintosh; Intel Mac OS X)');

var url = casper.cli.args[0];

var rval;
casper.thenOpen(url);
casper.then(function() {
    rval = this.evaluate(function() { 
        try {
            var $title = $('title');
            var title = $title.length ? $title.first().html() : null;

            //console.log(title);
            var links = [];
            var getLink = function($o) {
                if (! $o) {
                    return null;
                }
                var hr = $o.attr('href');
                // WTF, this is how you have to convert a relative to an absolute URL
                return $('<a>').attr('href', hr).get(0).href;
            };
            var l;
            l = getLink($('link[rel=apple-touch-icon]'));
            if (l)  {
                links.push(l);
            }
            l = getLink($('link[rel="shortcut icon"]'));
            if (l)  {
                links.push(l);
            }
            l = getLink($('link[rel="icon"]'));
            if (l)  {
                links.push(l);
            }
            return {'title': title, 'icons': links};
/*            var x = $('link[rel="shortcut icon"]');
            console.log('hello3');
            console.log(x.attr('href'));
            console.log('hello4');*/
        } catch(e) {
            console.log(JSON.stringify(e));
        } 

    });

    require('utils').dump(rval);


}
);



casper.run();
