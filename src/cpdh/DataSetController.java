package cpdh;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;


public class DataSetController {
	
	@FXML
	private Label infoLabel, infoLabel2;
	@FXML
	private TextField textfield;
	@FXML
	private Hyperlink hyperlinkMakeDefault;
	@FXML
	private ProgressBar progressBar;
	@FXML
	private Button btnConstructDataSets;
	
	boolean dataSetsAreLoaded  = false;
	boolean dataSetsAreDefault = false; 
	
	File ini = new File("settings.ini");
	File defaultDataSetDirectory = new File(".");
	File currentDataSetDirectory = defaultDataSetDirectory;
	Map<Integer, CpdhDataSet> dataSets = new HashMap<>();
	Task< Map<Integer, CpdhDataSet>> constructDataSets;
	
	final private DirectoryChooser directoryChooser = new DirectoryChooser();
	ExecutorService executor;
	
	@FXML
	private void initialize() {
		
		progressBar.setVisible(false);
		progressBar.setManaged(false);
		directoryChooser.setInitialDirectory(new File("."));
		
		try {
			currentDataSetDirectory = defaultDataSetDirectory = getDefaultDataSetLocation();
		} catch (IOException e) {
			infoLabel.setText("Unable to load initial data set settings.");
			e.printStackTrace();
			return;
		}
		
		textfield.setText(currentDataSetDirectory.getAbsolutePath());
		try {
			loadDataSets(currentDataSetDirectory);
		} catch (IOException e) {
			dataSetsAreLoaded = false;
			dataSetsAreDefault = false;
			return;
		}
		dataSetsAreLoaded  = true;
		dataSetsAreDefault = true;
		directoryChooser.setInitialDirectory(currentDataSetDirectory.getParentFile());
	}
	
	void onShow() {
		
		if (dataSetsAreLoaded) {
			infoLabel.setText("Data set: \"" + currentDataSetDirectory.getName() + "\" is successfully loaded.");
			infoLabel2.setText(dataSetInfo());
		}
		else {
			infoLabel.setText("No data found for selected folder!");
			infoLabel2.setText("To construct data set from the content of selected folder, press Build data set button.");				
		}
		
		if (dataSetsAreDefault) {
			hyperlinkMakeDefault.setVisible(false);
		}
		else if (dataSetsAreLoaded){
			hyperlinkMakeDefault.setVisited(false);	
			hyperlinkMakeDefault.setVisible(true);
		}
	}

	private File getDefaultDataSetLocation() throws IOException {

			String defaultDir = Files.readString(ini.toPath());
			return new File(defaultDir);
	}
	
	@FXML
	private void handleSetDefault() {

		defaultDataSetDirectory = currentDataSetDirectory;
		try {
			writeDefaultDataSetLocation();
		} catch (IOException e) {
			infoLabel2.setText("Unable to write to " + ini.getName() +" file.");
			e.printStackTrace();
		}
		dataSetsAreDefault = true;
		infoLabel2.setText("Set as default data set.");
		hyperlinkMakeDefault.setVisible(false);
	}
	
	private void writeDefaultDataSetLocation() throws IOException {
		
		Files.writeString(ini.toPath(), defaultDataSetDirectory.getAbsolutePath(),
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}
	
	@FXML
	private void handleBtnBrowse() {
		
		var settingsWindow = textfield.getScene().getWindow();
		File dir = directoryChooser.showDialog(settingsWindow);
		if (dir == null)
			return;
		textfield.setText(dir.getAbsolutePath());
		currentDataSetDirectory = dir;
		directoryChooser.setInitialDirectory(dir.getParentFile());
		try {
			loadDataSets(dir);
		} catch (IOException e) {
			dataSetsAreLoaded = false;
			infoLabel.setText("No data found for selected folder!");
			infoLabel2.setText("To construct data set from the content of selected folder, press Construct data button.");
			hyperlinkMakeDefault.setVisible(false);
			return;
		}
		dataSetsAreLoaded = true;
		infoLabel.setText("Data set: \"" + dir.getName() + "\" is successfully loaded.");
		infoLabel2.setText(dataSetInfo());
		if (dir.equals(defaultDataSetDirectory.getAbsoluteFile())) {
			dataSetsAreDefault = true;
			hyperlinkMakeDefault.setVisible(false);
		} else {
			dataSetsAreDefault = false;
			hyperlinkMakeDefault.setVisible(true);
			hyperlinkMakeDefault.setVisited(false);			
		}
	}
	
	private void loadDataSets(File dir) throws IOException {
	
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
	private void handleConstructDataSet() {
		
		String previousInfo = infoLabel.getText(); 
		if (constructDataSets != null)
			constructDataSets.cancel(true);
		constructDataSets = Tasks.newConstructDataSetsTask(currentDataSetDirectory);
		
		constructDataSets.setOnRunning( event -> 
		progressBar.progressProperty().bind(constructDataSets.progressProperty()) );

		constructDataSets.setOnSucceeded(event -> {
			try {
				dataSets = constructDataSets.get();
				dataSets.forEach((key, dataSet) -> {
					String filename = currentDataSetDirectory.getName() + " data set " + key + ".txt";
					dataSet.writeTo(new File(currentDataSetDirectory, filename));
				});
				infoLabel.setText("Data set is constructed and saved.");
			} catch (InterruptedException | ExecutionException e) {
				infoLabel.setText("Unable to construct the data set.");
				e.printStackTrace();
			}
			progressBar.progressProperty().unbind();
			progressBar.setVisible(false);
			progressBar.setManaged(false);
			infoLabel2.setText(dataSetInfo());
			infoLabel2.setVisible(true);
			btnConstructDataSets.setDisable(false);
		});

		constructDataSets.setOnCancelled(event -> {
			progressBar.progressProperty().unbind();
			progressBar.setVisible(false);
			progressBar.setManaged(false);
			infoLabel.setText(previousInfo);
			infoLabel2.setVisible(true);
			btnConstructDataSets.setDisable(false);
		});
		
		executor.submit(constructDataSets);
		btnConstructDataSets.setDisable(true);
		infoLabel.setText("Constructing the data set ... ");
		infoLabel2.setVisible(false);
		progressBar.setVisible(true);
		progressBar.setManaged(true);
	}
	
	void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}
	
	CpdhDataSet getDataSet(Integer key) {
		return dataSets.get(key);
	}
	
	private String dataSetInfo() {
		
		if (dataSets.isEmpty()) 
			return "";
		
		return String.format("There are %d categories and total of %d shapes in the data set.",
				dataSets.get(50).numOfCategories(), dataSets.get(50).numOfCpdhs() );
	}
	
	@FXML
	void handleBtnClose() {
		onClose();
		textfield.getScene().getWindow().hide();
	}
	
	void onClose() {
		if (constructDataSets != null)
			constructDataSets.cancel(true);
	}
}
