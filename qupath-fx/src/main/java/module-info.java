module qupath.fx {
    requires java.prefs;

    requires javafx.base;
    requires javafx.controls;

    requires org.slf4j;
    requires org.controlsfx.controls;

    exports qupath.fx.controls;
    exports qupath.fx.dialogs;
    exports qupath.fx.localization;
    exports qupath.fx.prefs;
    exports qupath.fx.utils;
}