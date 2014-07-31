#!/usr/bin/env python

import os
import tempfile
import unittest
import stat

from compile_html_deps import *


class TestWhitespaceInLine(unittest.TestCase):
    def testSimpleHtmlParsing(self):
        scripts = parse_html('''    <script src="../js/config.js"></script>''')
        assert len(scripts) == 1 and scripts[0] == '../js/config.js'
        scripts = parse_html('''    <script src='../js/config.js'></script>''')
        assert len(scripts) == 1 and scripts[0] == '../js/config.js'

        scripts = parse_html('''    <script type="text/javascript" src='../js/config.js'></script>''')
        assert len(scripts) == 1 and scripts[0] == '../js/config.js'
        scripts = parse_html('''    <script type="text/javascript"> this is not a link</script>''')
        assert not scripts

    def testSimpleScriptRemoval(self):
        new_html = remove_scripts('''    <script src="../js/config.js"></script>''')
        assert not new_html.strip()
        new_html = remove_scripts('''    <script src="../js/config.js"></script>
            <script src="../js/config2.js"></script>''')        
        assert not new_html.strip()

        new_html = remove_scripts('''    <script src="../js/config.js"></script>
            <script type="text/javascript"> this is not a link</script>''')        
        assert new_html.strip() == '''<script type="text/javascript"> this is not a link</script>'''




    def testSimpleScriptReplacement(self): 
        #TODO: maybe these should be in files
        IN_HTML = '''
<!DOCTYPE html>
<html class="extension-popup">
<head>
    <script src="../js/config.js"></script>
    <script src="../js/logging.js"></script>
    <script src="../js/jquery.min.js"></script>
    <script src="../js/jquery.ba-resize.min.js"></script>
    <script src="../js/bootstrap.min.js"></script>
    <script src="../js/underscore-min.js"></script>
    <script src="../js/URI.js"></script>
    <script src="../js/admin-common.js"></script>
    <script src="../js/querystring.js"></script>
    <script src="../js/userpass.js"></script>
    <script src="../domain.js"></script>
    <script src="../js/client.js"></script>
    <script src="../js/helpers.js"></script>
    <script src="../js/popup.js"></script>

    <link rel="stylesheet" type="text/css" href="../css/mitro.css" />
    <link rel="stylesheet" type="text/css" href="../css/style.css" />
</head>
<body>
  STUFF
</body>
</html>
'''

        OUT_HTML = '\n<!DOCTYPE html>\n<html class="extension-popup">\n<head>\n    \n    \n    \n    \n    \n    \n    \n    \n    \n    \n    \n    \n    \n    \n\n    <link rel="stylesheet" type="text/css" href="../css/mitro.css" />\n    <link rel="stylesheet" type="text/css" href="../css/style.css" />\n\n<script type="text/javascript" src="%s"></script>\n</head>\n<body>\n  STUFF\n</body>\n</html>\n'
        OUT_HTML_NOMIN = '\n<!DOCTYPE html>\n<html class="extension-popup">\n<head>\n    \n    \n    <script src="../js/jquery.min.js"></script>\n    <script src="../js/jquery.ba-resize.min.js"></script>\n    <script src="../js/bootstrap.min.js"></script>\n    <script src="../js/underscore-min.js"></script>\n    \n    \n    \n    \n    \n    \n    \n    \n\n    <link rel="stylesheet" type="text/css" href="../css/mitro.css" />\n    <link rel="stylesheet" type="text/css" href="../css/style.css" />\n\n<script type="text/javascript" src="%s"></script>\n</head>\n<body>\n  STUFF\n</body>\n</html>\n'

        new_html = replace_scripts(IN_HTML, 'foo')
        self.assertEquals(OUT_HTML % 'foo', new_html)

        new_html = replace_scripts(IN_HTML, 'foo', True)
        self.assertEquals(OUT_HTML_NOMIN % 'foo', new_html)




if __name__ == '__main__':
    unittest.main()
