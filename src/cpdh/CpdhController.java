package cpdh;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;


public class CpdhController {
	
	@FXML
	private BorderPane borderPane;
	@FXML
	private Button btnAutoCompare;
	@FXML
	private RadioMenuItem radMeni50, radMeni100, radMeni250;
	@FXML
	private ImageView imageView;
	@FXML
	private TextField textField;
	@FXML
	private TextArea textArea;
	@FXML
	private RadioButton rBtnOriginal, rBtnBinary, rBtnContour, rBtnPoints, rBtnCircle, rBtnRegions;
	@FXML
	private ToggleGroup rbToggleGroup, menuToggleGroup;
	@FXML
	private Label label;
	@FXML
	private ProgressBar progresBar;

	private HostServices hostServices;
	
	private final FileChooser fileChooser = new FileChooser();
//	private File lastOpenedImageFile;
	
	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private Map<Integer,Cpdh> cpdhs = new HashMap<>();
	private Cpdh selectedCpdh;
	private DataSetController dataSetController;
	private Stage dataSetSettingsWindow;

	@FXML
	private void initialize() {

		/* configure handling of key pressed events */
		borderPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
			switch (event.getCode()) {
			
			case DIGIT1:
			case NUMPAD1:
				rBtnOriginal.setSelected(true);
				event.consume();
				break;
			case DIGIT2:
			case NUMPAD2:
				rBtnBinary.setSelected(true);
				event.consume();
				break;
			case DIGIT3:
			case NUMPAD3:
				rBtnContour.setSelected(true);
				event.consume();
				break;
			case DIGIT4:
			case NUMPAD4:
				rBtnPoints.setSelected(true);
				event.consume();
				break;
			case DIGIT5:
			case NUMPAD5:
				rBtnCircle.setSelected(true);
				event.consume();
				break;
			case DIGIT6:
			case NUMPAD6:
				rBtnRegions.setSelected(true);
				event.consume();
				break;
			default:
				break;
			}
		});

		/* configure DataSetController & window */
		FXMLLoader loader = new FXMLLoader(getClass().getResource("DataSetUI.fxml"));
		Parent root = null;
		try {
			root = loader.load();
		} catch (IOException e) {
			e.printStackTrace();
		}
		dataSetController = loader.getController();
		dataSetController.setExecutor(executorService);
		dataSetSettingsWindow = new Stage();
		dataSetSettingsWindow.setScene(new Scene(root));
		dataSetSettingsWindow.setTitle("Data set settings");
		dataSetSettingsWindow.initModality(Modality.APPLICATION_MODAL);
		dataSetSettingsWindow.setOnShowing(event -> dataSetController.onShow());
		dataSetSettingsWindow.setOnCloseRequest(event -> dataSetController.onClose());
		
		/* configure menuToggleGroup */

			/* configure selection of numOfPoints */
		radMeni50 .setUserData(Integer.valueOf(50));
		radMeni100.setUserData(Integer.valueOf(100));
		radMeni250.setUserData(Integer.valueOf(250));
		
		menuToggleGroup.selectedToggleProperty().addListener( (observable, oldValue, newValue) -> {
			textField.setText("");
			label.setText("");
			selectedCpdh = cpdhs.get(newValue.getUserData());
			if (selectedCpdh != null) {
				textArea.setText("CPDH descriptor values:\n" + selectedCpdh.toString());
				configRadBtn(selectedCpdh.getImages());
			}
			else {
				textArea.setText("");
				configRadBtn(List.of());
			}
		});
		
		/* configure rbToggleGroup */
		rbToggleGroup.selectedToggleProperty().addListener( (observable, oldValue, newValue) -> {
			Image image = (Image) newValue.getUserData();
			imageView.setImage(image);
		});

		/* configure fileChooser */
		fileChooser.setTitle("Choose image file to open.");
		fileChooser.getExtensionFilters().clear();
		fileChooser.getExtensionFilters().add(
			new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jepg", "*.bmp"));
		File iniDir = new File("pictures");
		if ( ! iniDir.isDirectory()) 
			iniDir = new File(".");
		fileChooser.setInitialDirectory(iniDir);
		
		progresBar.setManaged(false);	
		
		// Automatic compare not implemented
		btnAutoCompare.setDisable(true);
		btnAutoCompare.setVisible(false);
		btnAutoCompare.setManaged(false);
	}

	@FXML
	private void handleBtnLoadImage() {
		handleItemLoadImage();
	}
	
	@FXML
	private void handleBtnRetrieveShape() {
		handleItemRetrieveShape();
	}
	
	@FXML
	private void handleItemLoadImage() {
		
		label.setText("");
		final File file = fileChooser.showOpenDialog(imageView.getScene().getWindow());
		if (file != null) {
			processFile(file);
			textField.setText("");
			textArea.setText("CPDH descriptor values:\n" + selectedCpdh.toString());
			configRadBtn(selectedCpdh.getImages());
			fileChooser.setInitialDirectory(file.getParentFile());
		}
	}
	
	private void processFile(File file) {
		
		menuToggleGroup.getToggles().forEach( numberOfPointsSetting -> {
			Integer numOfPoints = (Integer) numberOfPointsSetting.getUserData();
			cpdhs.put(numOfPoints, new Cpdh(file, numOfPoints));
		});
		Integer selectedNumOfPoints = (Integer) menuToggleGroup.getSelectedToggle().getUserData();
		this.selectedCpdh = cpdhs.get(selectedNumOfPoints);
	}
	
	@FXML
	private void handleItemRetrieveShape() {
		
		CpdhDataSet dataSet = null;
		if (selectedCpdh != null) {
			Integer selectedNumOfPoints = (Integer) menuToggleGroup.getSelectedToggle().getUserData();
			dataSet = dataSetController.getDataSet(selectedNumOfPoints);
			if (dataSet == null || dataSet.isEmpty()) {
				label.setText("Please construct or load CPDH data set first.");
				return;
			}
			textField.setText("");
			progresBar.setProgress(-1);
			progresBar.setVisible(true);
			progresBar.setManaged(true);
			label.setText(" Searching ...");
			
			Task<String> matchCpdhToGroup = Tasks.newMatchCpdhToGroupTask(selectedCpdh, dataSet);
			matchCpdhToGroup.setOnSucceeded(event -> {
				try {
					textField.setText("Best Match: " + matchCpdhToGroup.get());
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				} finally {
					progresBar.setVisible(false);
					progresBar.setManaged(false);
					label.setText(matchCpdhToGroup.getMessage());
				}
			});
			executorService.submit(matchCpdhToGroup);
		}
		else 
			label.setText("Please load image first.");
	}

	@FXML
	private void handleItemDataSetSettings() {
		dataSetSettingsWindow.showAndWait();
	}	
	
	private void configRadBtn(List<Image> images) {

		if (images.size() == 6) {
			ListIterator<Image> itr = images.listIterator();
			rBtnOriginal.setUserData( itr.next());
//			rBtnGray	.setUserData( itr.next());
			rBtnBinary	.setUserData( itr.next());
			rBtnContour	.setUserData( itr.next());
			rBtnPoints	.setUserData( itr.next());
			rBtnCircle	.setUserData( itr.next());
			rBtnRegions	.setUserData( itr.next());
		} 
		else {
			rbToggleGroup.getToggles().forEach( rBtn -> {
				rBtn.setUserData(null);
			});
		}
		imageView.setImage( (Image) rbToggleGroup.getSelectedToggle().getUserData());
	}
	
	@FXML
	private void handleItemQuit() {
		Platform.exit();
	}
	
	@FXML
	private void handleItemHelp() {
		Alert help = new Alert(AlertType.INFORMATION);
		help.setTitle("Cpdh App help");
		help.setHeaderText("Cpdh App help");
		help.setContentText("For best result it is recommended to use images that contain only a single object (single contour)"
				+ "\nand that object is clearly distinguishable from the background."
				+ "\n\nA data set can easily be created from the content of a folder."
				+ "\nTo construct custom data set simply put pictures in a folder and select that folder as data set in Data set settings section."
				+ "\nAfter that simply click build data set button."
				+ "\nFile names of pictures are important as groups of shapes are inferred from the pictures' file name."
				+ "\nFile name should begin with a name of a shape, followed by a dash \"-\" and the rest of the file name."
				+ "\nFor example \"butterfly-01\" and \"butterfly-small\" will be part of the same group of shapes,"
				+ "\nbut \"other-butterfly\" will not be placed in that group."
				+ "\nAfter adding or removing pictures from the folder, data set needs to be rebuilt for changes to take effect."
				+ "\n\nSupported file extensions are: .jpg, .jpeg and .bmp .");
		help.setOnShown(e -> {
			help.getDialogPane().setMinWidth(800);
			help.setResizable(false);
			});	
		help.showAndWait();
	}
	
	@FXML
	private void handleItemAbout() {
		
		Alert info = new Alert(Alert.AlertType.INFORMATION);
		info.setTitle("Cpdh App About");
		info.setHeaderText("Cpdh App\n"
				+ "ver. 0.1.0-rc1");
		StringBuilder contentBuilder = new StringBuilder("This app constructs CPDH descriptor and shows steps made during the construction.")
				.append("\nThis CPDH descriptor then can be run through a data set to find the most similar match.")
				.append("\nMPEG-7 shape dataset is provided as default, but for the best results, constructing custom data set is recommended.")
				.append("\nMore information about how to create a data set can be found under Help.")
				.append("\n\nTo learn more about CPDH descriptor, please visit:");
		Label about = new Label(contentBuilder.toString());
		Hyperlink link = new Hyperlink("https://www.researchgate.net/publication/220611301"
				+ "_A_novel_contour_descriptor_for_2D_shape_matching_and_its_application_to_image_retrieval");
		link.setOnAction(e -> hostServices.showDocument( link.getText()) );
		Label author = new Label("\n\n Author:\t\t Aleksandar Ujfaluši\n\t\t\t AlexUjfa@outlook.com");
		VBox contentBox = new VBox(about, link, author);
		info.getDialogPane().setContent(contentBox);
		info.setOnShown(e -> {
			info.getDialogPane().setMinWidth(800);
			info.setResizable(false);
		});
		info.showAndWait();
	}
	
	void shutDown() {
		executorService.shutdown();
		dataSetSettingsWindow.close();
		try {
			boolean terminated = executorService.awaitTermination(300, TimeUnit.MILLISECONDS);
			if (!terminated) 
				executorService.shutdownNow();
		} catch (InterruptedException e) {
			System.out.println("Failed to shut down.");
			e.printStackTrace();
		}
	}

	void setHostServices(HostServices hostServices) {
		this.hostServices = hostServices;
	}

}
