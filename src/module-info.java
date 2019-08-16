
/**
 * @author Aleksandar
 *
 */

module cpdh {
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.swing;
	requires transitive javafx.graphics;
	requires opencv;
	requires java.desktop;
	
	opens cpdh to javafx.fxml;
	exports cpdh;
}
