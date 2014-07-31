# SDK Widgets on toolbars
This Jetpack module extends the `sdk/widget` module with a few additional properties, allowing Firefox Add-on developers to easily place widgets on toolbars.

## Usage
The API is identical to [`sdk/widget`](https://addons.mozilla.org/en-US/developers/docs/sdk/latest/modules/sdk/widget.html) (only new properties were added).

Here's an example, based on the first example from the [`sdk/widget` documentation](https://addons.mozilla.org/en-US/developers/docs/sdk/1.14/modules/sdk/widget.html#Creation%20and%20Content). The created widget is not placed on the addon bar, but on the navigation bar.

```javascript
require("toolbarwidget").ToolbarWidget({
    toolbarID: "nav-bar", // <-- Place widget on Navigation bar
    id: "mozilla-icon",
    label: "My Mozilla Widget",
    contentURL: "http://www.mozilla.org/favicon.ico"
});
```

`ToolbarWidget` creates a `sdk/widget` instance, moves it to the desired toolbar, and returns the `Widget` instance.  
This instance has a read-only property called `toolbarID`. If you want to move the widget, destroy it and create it again.

## Quick reference
See [docs/toolbarwidget.md] for more details. Here is a concise reference for the `ToolbarWidget` constructor:

- `toolbarID` - string. The widget will be put on the toolbar with this ID.
- `insertBefore - optional string. Put widget before the XUL element with this ID.
- `forceMove` - optional boolean. Whether to force the widget to be placed at the specified location.
- `height` - optional number. The height of the widget.
- `autoShrink` - optional boolean, default `true`. Whether to reduce the widget's height when the specified height exceeds
   the toolbar's height. When set to true, the user's icon size preference will automatically be respected.
- `aspectRatio` - optional number. Set this property to force the width to depend on the height (width = height / aspectRatio).

Plus all methods and properties from the [`sdk/widget` module](https://addons.mozilla.org/en-US/developers/docs/sdk/latest/modules/sdk/widget.html)

## Installation
You can add the module globally (in the `packages` directory under the SDK root), to make it available to all of your Jetpack projects,
or add it to a single project (in the `packages` directory under your add-on's root).

The official documentation contains a [tutorial on installing third-party modules](https://addons.mozilla.org/en-US/developers/docs/sdk/latest/dev-guide/tutorials/adding-menus.html),
which suggests to download and extract an archive.  
I strongly recommend to use git for this purpose, because it makes package management *a lot easier*. For example:

```sh
# Go to the packages directory of the SDK's root.
cd /opt/addon-sdk/packages
# Clone the repository (creates a directory "toolbarwidget-jplib")
git clone git://github.com/Rob--W/toolbarwidget-jplib.git
# Done! You may want to update and view the documentation...
addon-sdk && cfx sdocs
# Later, when you want to update the package to the latest version...
cd /opt/addon-sdk/packages/toolbarwidget-jplib
git pull
```

After installing the module, declare the dependency in [package.json](https://addons.mozilla.org/en-US/developers/docs/sdk/latest/dev-guide/package-spec.html):

```js
    ...
    "dependencies": ["toolbarwidget"],
    ...
```

## Dependencies
The following standard Jetpack modules were used:

- [`sdk/windows/utils`](https://addons.mozilla.org/en-US/developers/docs/sdk/1.14/modules/sdk/window/utils.html)
- [`sdk/widget`](https://addons.mozilla.org/en-US/developers/docs/sdk/1.14/modules/sdk/widget.html)

## Related
- [`browser-action`](https://github.com/Rob--W/browser-action-jplib) - Jetpack module which brings Google Chrome's `chrome.browserAction` API to Firefox.

## Credits
Created by Rob Wu <gwnRob@gmail.com>.  
Inspired by Erik Void's [toolbarbutton](https://github.com/voldsoftware/toolbarbutton-jplib), a module which allows one to easily create simple toolbar buttons.

Released under a MIT license.
