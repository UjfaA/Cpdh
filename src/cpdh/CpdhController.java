package cpdh;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.concurrent.Task;
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
				Integer dataSetKey = (Integer) newValue.getUserData();
				selectedDataSet = dataSets.get(dataSetKey);
			}
			// clear previously loaded cpdh and associated images;
			cpdh = null;
			textField.clear();
			textArea.clear();
			configRadBtn(List.of());
		});
		
		/* configure rbToggleGroup */
		rbToggleGroup.selectedToggleProperty().addListener( (observable, oldValue, newValue) -> {

			Image image = (Image) newValue.getUserData();
			imageView.setImage(image);
		});
		
		/* try to load data sets from default location */
		try {
			dataSets = new HashMap<>();
			loadDS(new File("pictures\\MPEG-7 shape"), dataSets);	
			label.setText("Data sets successfully loaded");
			Integer dataSetKey = (Integer) meniToggleGroup.getSelectedToggle().getUserData();
			selectedDataSet = dataSets.get(dataSetKey);
		} catch (Exception e) {
			label.setText("Unable to load data sets at start.");
			e.printStackTrace();
		}
		
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
		
		label.setText("");
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
			executorService.execute(processFile);
		}
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
		else
			label.setText("");
		
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
	private void handleItemloadDS() throws IOException {
		
		directoryChooser.setTitle("Select folder that contains data set.");
		directoryChooser.setInitialDirectory(new File("pictures"));
		File dir = directoryChooser.showDialog(imageView.getScene().getWindow());
		if (dir != null) {
			
			if (dataSets == null)
				dataSets = new HashMap<>();
			try {
				loadDS(dir, dataSets);
				label.setText("Data sets successfully loaded");
			} catch (Exception e) {
				label.setText("Canceled. " +e.getMessage());
				e.printStackTrace();
			}
			Integer dataSetKey = (Integer) meniToggleGroup.getSelectedToggle().getUserData();
			selectedDataSet = dataSets.get(dataSetKey);
		}
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
					Integer dataSetKey = (Integer) meniToggleGroup.getSelectedToggle().getUserData();
					selectedDataSet = dataSets.get(dataSetKey);
					writeDStoFile(dataSets.get(50), dir, 50);
					writeDStoFile(dataSets.get(100), dir, 100);
					writeDStoFile(dataSets.get(250), dir, 250);
					progresBar.progressProperty().unbind();
					progresBar.setVisible(false);
					label.setText("Data sets are constructed and saved.");
				} catch (InterruptedException | ExecutionException e) {
					label.setText("Unable to construct data sets");
					e.printStackTrace();
				}
			});
			constructAllDataSets.setOnCancelled(event -> {
				dataSets = null;
				selectedDataSet= null;
				progresBar.progressProperty().unbind();
				progresBar.setVisible(false);
				label.setText("Constructing data set cancelled.");
				//TODO message user ?
			});
			executorService.execute(constructAllDataSets);
			
			progresBar.setProgress(0);
			progresBar.progressProperty().bind(constructAllDataSets.progressProperty());
			progresBar.setVisible(true);
			label.setText("Constructing data sets ... ");
		}
	}
	
//	TODO: check results of listFiles() for wrong results;
	private void loadDS(File dir, Map< Integer,List<List<Cpdh>> > dataSets) throws Exception {
		
		/* Simple solution for 3 files with exact file name */

		Integer[] dataSetKeys = {Integer.valueOf(50), Integer.valueOf(100), Integer.valueOf(250)};
		File[]	dataSetsFiles = { new File(dir, "MPEG-7 shape data set 50.txt"),
								new File(dir, "MPEG-7 shape data set 100.txt"),
								new File(dir, "MPEG-7 shape data set 250.txt")};
		
		/* More robust solution */
/*		
		Integer[] dataSetKeys = {Integer.valueOf(50), Integer.valueOf(100), Integer.valueOf(250)};
		File[] dataSetsFiles = dir.listFiles((directory, name) -> {
			return name.startsWith("MPEG-7") &&
					(	name.endsWith("data set " + dataSetKeys[0] + ".txt") || 
						name.endsWith("data set " + dataSetKeys[1] + ".txt") || 
						name.endsWith("data set " + dataSetKeys[2] + ".txt") 
					);
		});
		if (dataSetKeys.length != dataSetsFiles.length)
			throw new Exception(String.format("Expected %d data set files, but found %d files.", 
														dataSetKeys.length, dataSetsFiles.length));
		
		Arrays.sort(dataSetsFiles, (file1, file2) -> {
			
			String name1 = file1.getName();
			int BeforeNum = name1.lastIndexOf(' ');
			int dot = name1.lastIndexOf('.');
			int number1 = Integer.parseUnsignedInt(name1, BeforeNum + 1, dot, 10);
			
			String name2 = file2.getName();
			BeforeNum = name2.lastIndexOf(' ');
			dot = name2.lastIndexOf('.');
			int number2 = Integer.parseUnsignedInt(name2, BeforeNum + 1, dot, 10);
			
			return Integer.compare(number1, number2);
		});
*/
		for (int i = 0; i < dataSetsFiles.length; i++) {
			List<String> lines = Files.readAllLines(dataSetsFiles[i].toPath());
			List<List<Cpdh>> dataSet = new ArrayList<List<Cpdh>>();
			for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
				int group 		= lineNum / 20;
				int position	= lineNum % 20;
				if (position == 0) //add new group
					dataSet.add(new ArrayList<Cpdh>());
				dataSet.get(group).add(new Cpdh(lines.get(lineNum)));
			}
			dataSets.put(dataSetKeys[i], dataSet);
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
		else {
			rbToggleGroup.getToggles().forEach( rBtn -> {
				rBtn.setUserData(null);
			});
		}
		if (rbToggleGroup.getSelectedToggle() != rBtnOriginal) // same RadioButton instance.	
			rbToggleGroup.selectToggle(rBtnOriginal);
		else {
			Image image = (Image) rBtnOriginal.getUserData();
			imageView.setImage(image);
		}
		rBtnOriginal.requestFocus();	
	}
	
	@FXML
	private void handleItemQuit() {
		Platform.exit();
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

}
