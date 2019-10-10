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
import javafx.scene.Node;
import javafx.scene.control.Alert;
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
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;


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
	private final DirectoryChooser directoryChooser = new DirectoryChooser();
	private File lastOpenedImageFile;
	
	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private Cpdh cpdh;
	private CpdhDataSet selectedDataSet;
	private Map<Integer, CpdhDataSet> dataSets;

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

		/* configure selection of numOfPoints */
		radMeni50.setUserData(Integer.valueOf(50));
		radMeni100.setUserData(Integer.valueOf(100));
		radMeni250.setUserData(Integer.valueOf(250));
		
		/* configure meniToggleGroup */
		menuToggleGroup.selectedToggleProperty().addListener( (observable, oldValue, newValue) -> {
			label.setText("");
			if (dataSets != null) {
				Integer dataSetKey = (Integer) newValue.getUserData();
				selectedDataSet = dataSets.get(dataSetKey);
			}
			/* reconstruct cpdh with new number of points */
			if (lastOpenedImageFile != null) {
					processFile(lastOpenedImageFile);
			}
		});
		
		/* configure rbToggleGroup */
		rbToggleGroup.selectedToggleProperty().addListener( (observable, oldValue, newValue) -> {
			((Node) newValue).requestFocus();
			Image image = (Image) newValue.getUserData();
			imageView.setImage(image);
		});

		/* try to load data sets from default location */
		File dataSetsDir = new File("pictures\\MPEG-7 shape");
		if (dataSetsDir.isDirectory()) {
			try {
				dataSets = new HashMap<>();
				loadDataSets(dataSetsDir, dataSets);	
				label.setText("MPEG-7 shape data set successfully loaded.");
				Integer dataSetKey = (Integer) menuToggleGroup.getSelectedToggle().getUserData();
				selectedDataSet = dataSets.get(dataSetKey);
			} catch (Exception e) {
				label.setText("Unable to load data set from default location.");
				e.printStackTrace();
			}
		}
		else 
			label.setText("Unable to find data sets directory.");
		
		/* configure directoryChooser & fileChooser */
		File iniDir = new File("pictures");
		if ( ! iniDir.isDirectory()) iniDir = new File(".");
		directoryChooser.setInitialDirectory(iniDir);
		fileChooser.setInitialDirectory(iniDir);
		
		// functionality not yet implemented
		btnAutoCompare.setDisable(true);
		btnAutoCompare.setVisible(false);
		btnAutoCompare.setManaged(false);
		
		progresBar.setManaged(false);
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
		fileChooser.setTitle("Choose image file to open.");
		fileChooser.getExtensionFilters().clear();
		fileChooser.getExtensionFilters().add(
				new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jepg", "*.bmp"));
		final File file = fileChooser.showOpenDialog(imageView.getScene().getWindow());
		if (file != null) {
			fileChooser.setInitialDirectory(file.getParentFile());
			processFile(file);
		}
	}
	
	private void processFile(File file) {

		Integer numOfPoints = (Integer) menuToggleGroup.getSelectedToggle().getUserData();
		this.cpdh = new Cpdh(file, numOfPoints);
		textArea.setText(cpdh.toString());
		configRadBtn(cpdh.getImages());
		this.lastOpenedImageFile = file;
		textField.setText("");
	}
	
	@FXML
	private void handleItemRetrieveShape() {

		if (cpdh == null) {
			label.setText("Please load image first.");
			return;
		}
		else if (selectedDataSet == null || selectedDataSet.isEmpty()) {
			label.setText("Please construct or load CPDH data set first.");
			return;
		}
		else { 
			progresBar.setProgress(-1);
			progresBar.setVisible(true);
			progresBar.setManaged(true);
			label.setText(" Searching ...");
		}
		Task<String> matchCpdhToGroup = Tasks.newMatchCpdhToGroupTask(cpdh, selectedDataSet);
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

	@FXML
	private void handleItemloadDS() throws IOException {
		
		directoryChooser.setTitle("Select folder that contains data set.");
		final File dir = directoryChooser.showDialog(imageView.getScene().getWindow());
		if (dir != null) {
						
			if (dataSets == null)
				dataSets = new HashMap<Integer, CpdhDataSet>();
			try {
				loadDataSets(dir, dataSets);
				Integer dataSetKey = (Integer) menuToggleGroup.getSelectedToggle().getUserData();
				selectedDataSet = dataSets.get(dataSetKey);
				directoryChooser.setInitialDirectory(dir);
				label.setText("Data set successfully loaded");
			} catch (IOException e) {
				label.setText("Error due to:  " + e.getMessage());
				e.printStackTrace();
			} catch (NumberFormatException e) {
				label.setText("Error! Data set files might be corupted.");
				e.printStackTrace();
			}
		}
	}
	
	private void loadDataSets(File dir, Map<Integer, CpdhDataSet> dataSets) throws IOException {
			
		CpdhDataSet dataSet50  = new CpdhDataSetImp(50);
		CpdhDataSet dataset100 = new CpdhDataSetImp(100);
		CpdhDataSet dataSet250 = new CpdhDataSetImp(250);

		dataSet50 .loadFrom(new File(dir, dir.getName() + " data set 50.txt"));
		dataset100.loadFrom(new File(dir, dir.getName() + " data set 100.txt"));
		dataSet250.loadFrom(new File(dir, dir.getName() + " data set 250.txt"));

		dataSets.put(50, dataSet50);
		dataSets.put(100, dataset100);
		dataSets.put(250, dataSet250);
	}

	@FXML
	private void handleItemConstructDS() {
		
		directoryChooser.setTitle("Select folder that contains data set.");
		directoryChooser.setInitialDirectory(new File("pictures"));
		final File dir = directoryChooser.showDialog(imageView.getScene().getWindow());

		if (dir != null) { 

			var constructDataSets = Tasks.newConstructDataSetsTask(dir);
			
			constructDataSets.setOnSucceeded(event -> {
				try {
					dataSets = constructDataSets.get();
					Integer dataSetKey = (Integer) menuToggleGroup.getSelectedToggle().getUserData();
					selectedDataSet = dataSets.get(dataSetKey);
					dataSets.forEach((key, dataSet) -> {
						File file = new File(dir, dir.getName() + " data set " + key + ".txt");
						dataSet.writeTo(file);
					});
					label.setText("Data set is constructed and saved.");
				} catch (InterruptedException | ExecutionException e) {
					label.setText("Unable to construct data set.");
					e.printStackTrace();
				}
				progresBar.progressProperty().unbind();
				progresBar.setVisible(false);
				progresBar.setManaged(false);
			});
			
			constructDataSets.setOnCancelled(event -> {
				dataSets = null;
				selectedDataSet= null;
				progresBar.progressProperty().unbind();
				progresBar.setVisible(false);
				progresBar.setManaged(false);
				label.setText("Construction of the data set is cancelled.");
			});
			executorService.execute(constructDataSets);
			
			progresBar.setProgress(0);
			progresBar.progressProperty().bind(constructDataSets.progressProperty());
			progresBar.setVisible(true);
			progresBar.setManaged(true);
			label.setText("Constructing data sets ... ");
		}
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
	private void handleItemAbout() {
		
		Alert info = new Alert(Alert.AlertType.INFORMATION);
		info.setTitle("About");
		info.setHeaderText("Cpdh");
		StringBuilder contentBuilder = new StringBuilder("This app showcases CPDH descriptor.")
				.append("\nIt is intended to be used with MPEG-7 shape data set.")
				.append("\n\nTo learn more about CPDH descriptor, please visit:");
		Label about = new Label(contentBuilder.toString());
		Hyperlink link = new Hyperlink("https://www.researchgate.net/publication/220611301"
				+ "_A_novel_contour_descriptor_for_2D_shape_matching_and_its_application_to_image_retrieval");
		link.setOnAction(e -> {
			 hostServices.showDocument( link.getText());
		});
		Label author = new Label("\n\n Author:\t\t Aleksandar Ujfaluši\n\t\t\tAlexUjfa@outlook.com");
		VBox contentBox = new VBox(about, link, author);
		info.getDialogPane().setContent(contentBox);
		info.setOnShown(e -> {
			info.getDialogPane().setMinWidth(800);
			info.getDialogPane().getScene().getWindow().sizeToScene();
			info.setResizable(false);
		});
		info.showAndWait();
	}
	
	void shutDown() {
		executorService.shutdown();
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
