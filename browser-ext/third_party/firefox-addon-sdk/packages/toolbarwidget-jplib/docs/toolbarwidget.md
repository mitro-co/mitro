The `toolbarwidget` module enables you to create [widgets](modules/sdk/widget.html) and place it on any toolbar.

The API is identical to [`sdk/widget`](modules/sdk/widget.html), with the exception of the new properties listed below.
See the [`sdk/widget` documentation](modules/sdk/widget.html) for the full documentation.

## Example ##

    require("toolbarwidget").ToolbarWidget({
        toolbarID: "nav-bar", // Place widget on navigation bar
        height: 32,           // Change height. Default 16px, now at most 32px.
        id: "mozilla-icon",
        label: "My Mozilla Widget",
        contentURL: "http://www.mozilla.org/favicon.ico"
    });

<api name="ToolbarWidget">
@class

Represents a [Widget](modules/sdk/widgets.html).

<api name="ToolbarButton">
@constructor
Creates a new widget. The widget is immediately added to the specified toolbar.

@param options {object}
An object with [all keys from widget](modules/sdk/widget.html#Widget%29options%29) and the following key:

  @prop toolbarID {string}
    The id of the toolbar which you want to add the widget to.
    If invalid, it will be placed on the default addon bar.

    Example toolbar IDs:

    - **toolbar-menubar**: The menu bar.
    - **nav-bar**: The navigation bar.
    - **PersonalToolbar**: The bookmarks toolbar.
    - **TabsToolbar**: The tabs bar.
    - **addon-bar**: The addon bar.

  @prop insertbefore {string,array}
    Optional. Allows you to specify the ID of the XUL element, before which the widget should be placed.
    An ID is invalid when the XUL element is not found, or when the XUL element is not an immediate child of the toolbar.
    When an ID is invalid, the value is ignored and the next ID in the specified array is used.
    When all IDs are invalid, the widget is placed at the end of the toolbar.

    Example IDs:

    - **unified-back-forward-button**: Back/forward button container.
    - **urlbar-container**: URL bar container.
    - **search-container**: Container of search box.

  @prop forceMove {boolean}
    If true, the widget will be forced to stick at its position within the toolbar.

  @prop height {number}
    Optional (maximum) height in pixels of the widget. If not given, the sdk/widget's default height will be used.

  @prop autoShrink {boolean}
    Optional, default true. Whether to prevent the toolbar from growing in height when the button is added.
    This is done by decreasing the widget's height. Use media queries to adapt your widget to the correct size.
    This setting is enforced when the user changes the icon size preferences.

  @prop aspectRatio {number}
    Optional number. When this number is set, the width of the widget is automatically changed to match the
    widget's height. This value can only be used when the `height` property is set. When this property is used,
    the `width` property is useless and will not reflect the actual width of the widget.

</api>
<api name="toolbarID">
@property {string}
  The ID of the toolbar to which you've added the widget.  Read-only.
</api>
<api name="insertbefore">
@property {array}
  The id of the element which the toolbar widget should be inserted before.  Read-only.
</api>
<api name="forceMove">
@property {boolean}
  If true, the toolbar will be forced to stick at its position.
</api>
<api name="height">
@property {string}
  The (maximum) height of the widget. Setting it updates the widget's appearance immediately.
</api>
</api>
