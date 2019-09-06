package cpdh;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;



public class CpdhController {
	
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
	private ToggleGroup rbToggleGroup, meniToggleGroup;

	@FXML
	private Label label;
	@FXML
	private ProgressBar progresBar;
	
	private final FileChooser fileChooser = new FileChooser();
	private final DirectoryChooser directoryChooser = new DirectoryChooser();
	
	//TODO shut down on exit
	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private Cpdh cpdh;
	private List<List<Cpdh>> selectedDataSet;
	private Map< Integer, List<List<Cpdh>> > dataSets;
	
	
	@FXML
	private void initialize() {
		
		/* configure selection of numOfPoints */
		radMeni50.setUserData(Integer.valueOf(50));
		radMeni100.setUserData(Integer.valueOf(100));
		radMeni250.setUserData(Integer.valueOf(250));
		
		/* configure meniToggleGroup */
		meniToggleGroup.selectedToggleProperty().addListener( (observable, oldValue, newValue) -> {
			if (dataSets != null) {
				Integer dataSetKey = (Integer) meniToggleGroup.getSelectedToggle().getUserData();
				selectedDataSet = dataSets.get(dataSetKey);
			}
		});
		
		/* configure rbToggleGroup */
		rbToggleGroup.selectedToggleProperty().addListener( (observable, oldValue, newValue) -> {

			Image image = (Image) rbToggleGroup.getSelectedToggle().getUserData();
			imageView.setImage(image);
		});
		
		// functionality not yet implemented
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

		fileChooser.setTitle("Choose image file to open.");
		fileChooser.getExtensionFilters().clear();
		fileChooser.getExtensionFilters().add(
				new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jepg", "*.png", "*.bmp"));
		fileChooser.setInitialDirectory(new File(".\\pictures"));
		final File file = fileChooser.showOpenDialog(imageView.getScene().getWindow());

		if (file != null) {

			Task<Cpdh> processFile = new Task<Cpdh>() {

				@Override
				protected Cpdh call() throws Exception {
					int numOfPoints = (Integer) meniToggleGroup.getSelectedToggle().getUserData();
					return new Cpdh(file, numOfPoints);
				}
			};
			processFile.setOnSucceeded(event -> {
				try {
					this.cpdh = processFile.get();
					configRadBtn(cpdh.getImages());
					textField.setText("");
					textArea.setText(cpdh.toString());
					//						TODO Enable save meni
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
			executorService.submit(processFile);
		}
	}

	@FXML
	private void handleItemRetrieveShape() {

		if (cpdh == null) {
			label.setText("Please load image first.");
			return;
		}
		if (selectedDataSet == null || selectedDataSet.isEmpty()) {
			label.setText("Please construct or load CPDH data set first.");
			return;
		}
		
		Task<String> retrieveShape = new Task<>() {

			@Override
			protected String call() throws Exception {
				return cpdh.retrieveShape(selectedDataSet);					
			}
		};

		retrieveShape.setOnSucceeded(event -> {
			try {
				textField.setText("Best match : " + retrieveShape.get());
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

		executorService.execute(retrieveShape);
	}

	@FXML
	private void handleItemConstructDS() {
		
		directoryChooser.setTitle("Select folder that contains data set.");
		directoryChooser.setInitialDirectory(new File("pictures"));
		File dir = directoryChooser.showDialog(imageView.getScene().getWindow());

		if (dir != null) { 

			var constructAllDataSets = Tasks.newConstructDataSetsTask(dir);
			constructAllDataSets.setOnSucceeded(event -> {
				try {
					dataSets = constructAllDataSets.get();
					Integer datasetKey = (Integer) meniToggleGroup.getSelectedToggle().getUserData();
					selectedDataSet = dataSets.get(datasetKey);
					writeDStoFile(dataSets.get(50), dir, 50);
					writeDStoFile(dataSets.get(100), dir, 100);
					writeDStoFile(dataSets.get(250), dir, 250);
					progresBar.progressProperty().unbind();
					progresBar.setVisible(false);
					label.textProperty().unbind();
					label.setText("Data sets are constructed and saved.");
				} catch (InterruptedException | ExecutionException e) {
					label.setText("Unable to construct data sets");
					e.printStackTrace();
				}
			});
			constructAllDataSets.setOnCancelled(event -> {
				progresBar.progressProperty().unbind();
				progresBar.setVisible(false);
				label.textProperty().unbind();
				label.setText("Constructing data set cancelled.");
				//TODO message user ?
			});
			executorService.execute(constructAllDataSets);
			
			progresBar.progressProperty().bind(constructAllDataSets.progressProperty());
			progresBar.setVisible(true);
			label.textProperty().bind(constructAllDataSets.messageProperty());
		}
	}

	private void writeDStoFile(List<List<Cpdh>> dataSet, File dir, int sufix) {
		
		ArrayList<String> lines = new ArrayList<String>(1400);
		dataSet.forEach(group -> {
			group.forEach(cpdh -> {
				lines.add(cpdh.toString1Line());
			});
		});
		
		/* write to file */
		String fileName = dir.getName() + " data set " + sufix + ".txt"; 
		File file = new File( dir, fileName);
		try {
			Files.write(file.toPath(), lines, 
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);
		} catch (Exception e) {
			System.out.println("Error writing to file" + file.getName());
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
		if (rbToggleGroup.getSelectedToggle() != rBtnOriginal) { // same RadioButton instance.	
			rbToggleGroup.selectToggle(rBtnOriginal);
		}
		else {
			Image image = (Image) rBtnOriginal.getUserData();
			imageView.setImage(image);
		}
		rBtnOriginal.requestFocus();	
	}

}
