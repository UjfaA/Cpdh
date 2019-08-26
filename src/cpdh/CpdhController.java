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
import javafx.fxml.FXML;
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
	private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

	private Cpdh cpdh;
	private List<List<Cpdh>> selectedtDataSet;
	private Map< Integer, List<List<Cpdh>> > dataSets;
	
	
	@FXML
	private void initialize() {
		
		/* configure selection of numOfPoints */
		radMeni50.setUserData(Integer.valueOf(50));
		radMeni100.setUserData(Integer.valueOf(100));
		radMeni250.setUserData(Integer.valueOf(250));
		
		meniToggleGroup.selectedToggleProperty().addListener( (observable, oldValue, newValue) -> {
			Integer dataSetKey = (Integer) meniToggleGroup.getSelectedToggle().getUserData();
			if (dataSets != null)
				selectedtDataSet = dataSets.get(dataSetKey);
		});
		
		/* configure rbToggleGroup */
		rbToggleGroup.selectedToggleProperty().addListener( (observable, oldValue, newValue) -> {

			Image image = (Image) rbToggleGroup.getSelectedToggle().getUserData();
			imageView.setImage(image);
		});
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
					Cpdh cpdh = processFile.get();
					configRadBtn(cpdh.getImages());
					textField.setText("");
					textArea.setText(cpdh.toString());
					this.cpdh = cpdh;
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
		private void handleItemCompare() {
			
			if (cpdh == null) {
				label.setText("Please load image first.");
				return;
			}
			if (selectedtDataSet == null || selectedtDataSet.isEmpty()) {
				label.setText("Please load CPDH data set first.");
				return;
			}
			
//			int result = cpdh.runComparation(selectedtDataSet, SKIP_POSITION);
//			textField.setText("Grupa: " + String.valueOf(result) + " - " + GROUP_NAMES.get(result));
	
	
	//		
	//		/* učitaj sve datoteke */
	//		
	//		File dir = new File("pictures\\jpeg");
	//		if (!dir.isDirectory()) {
	//			System.out.println("NOT DIRECTORY");
	//		}
	//		 // file.listFiles() do not guarantee any order 
	//		File [] files = dir.listFiles((d, s) -> {
	//			return s.endsWith("-1.jpg") || s.endsWith("-01.jpg");
	//		});
	//		sort(files);
	//		
	//		/* write all files' names to file */
	//		ArrayList<String> sortedFiles = new ArrayList<String>(files.length);
	//		for (int i = 0; i < files.length; i++) {
	//			sortedFiles.add(files[i].getName());
	//		}
	//		try {
	//			File sortedFilesTxt = new File ("pictures\\output\\Sorted test files Auto.txt");
	//			Files.write(sortedFilesTxt.toPath(), sortedFiles,
	//					StandardOpenOption.CREATE,
	//					StandardOpenOption.WRITE,
	//					StandardOpenOption.TRUNCATE_EXISTING);
	//		} catch (IOException e) {
	//			System.out.println("Error");
	//			e.printStackTrace();
	//		}
	//
	//		/* obradi sve datoteke */
	//		
	//		ArrayList<String> outputLinesAuto = new ArrayList<String>(128);
	//		StringBuilder line = new StringBuilder(256);
	//		int grupa, brPogodaka = 0;
	//		try {
	//			
	//			for (int i = 0; i < files.length; i++) {
	//				File file = files[i];
	//				matSrc = Imgcodecs.imread(file.getPath());				
	//				processImage(matSrc);
	//				grupa = histogram.runComparation(dataset);
	//				if (grupa == i)
	//					brPogodaka += 1;
	//				line.delete(0, line.length())
	//				.append("datoteka: " ).append(String.format("%-20s", file.getName()) )
	//				.append("\t\točekivani rezultat: ").append(String.format("%-20s", GROUP_NAMES[i]))
	//				.append("\tdobijeni rezultat: ").append(String.format("%-20s",GROUP_NAMES[grupa]));
	//				outputLinesAuto.add(line.toString());
	//			}
	//		} catch (Exception e) {
	//			System.out.println("Error");
	//			e.printStackTrace();
	//		}
	//		
	//		double procenat = ((double) brPogodaka) / files.length;
	//		outputLinesAuto.add("Tačno prepoznato: " + brPogodaka + " slika. Tačnost: " + procenat);
	//		try {
	//			
	//			File outputFileAuto = new File ("pictures\\output\\Automatsko poređenje rezultat.txt");
	//			Files.write(outputFileAuto.toPath(), outputLinesAuto,
	//					StandardOpenOption.CREATE,
	//					StandardOpenOption.WRITE,
	//					StandardOpenOption.TRUNCATE_EXISTING);
	//		} catch (IOException e) {
	//			System.out.println("Error");
	//			e.printStackTrace();
	//		}
	//
			
				}

	@FXML
	private void handleItemConstructDS() {
		
		directoryChooser.setTitle("Select folder that contains data set.");
		directoryChooser.setInitialDirectory(new File("."));
		File dir = directoryChooser.showDialog(imageView.getScene().getWindow());

		if (dir != null) { 

			var constructCompleteDS = configureConstDSTask(dir);
			executorService.execute(constructCompleteDS);

			progresBar.progressProperty().bind(constructCompleteDS.progressProperty());
			progresBar.setVisible(true);
			label.setText("Constructing data sets ...");
		}
	}

	private Task< Map< Integer, List<List<Cpdh>> >> configureConstDSTask(File dir) {
		
		Task< Map< Integer, List<List<Cpdh>> > > constructCompleteDS = new Task<>() {
			
			@Override
			protected Map<Integer, List<List<Cpdh>> > call() throws Exception {
				
				File[] files = dir.listFiles((directory, name) -> {
					return name.endsWith(".jpg") || name.endsWith("jpeg") || name.endsWith(".png");
				});
				// file.listFiles() do not guarantee any order 
				sortFiles(files);
				updateProgress(1, 10);
				
				Map< Integer, List<List<Cpdh>> > dataSets = new ConcurrentHashMap<>();
				
				ConstructDSTask dataset50Task = new ConstructDSTask(files, 50);
				executorService.execute(dataset50Task);
				ConstructDSTask dataset100Task = new ConstructDSTask(files, 100);
				executorService.execute(dataset100Task);
				ConstructDSTask dataset250Task = new ConstructDSTask(files, 250);
				executorService.execute(dataset250Task);
				
				dataSets.put(50, dataset50Task.get());
				updateProgress(1, 3);
				writeDSToFile(dataSets.get(50), dir, 50);
				dataSets.put(100, dataset100Task.get());
				updateProgress(2, 3);
				writeDSToFile(dataSets.get(100), dir, 100);
				dataSets.put(250, dataset250Task.get());
				updateProgress(3, 3);
				writeDSToFile(dataSets.get(250), dir, 250);
								
				return dataSets;
			}

			private void writeDSToFile(List<List<Cpdh>> dataSet, File dir, int sufix) {
				
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
		
		};
		
		constructCompleteDS.setOnSucceeded( (event) -> {
			try {
				dataSets = constructCompleteDS.get();
				Integer dataSetKey = (Integer) meniToggleGroup.getSelectedToggle().getUserData();
				selectedtDataSet = dataSets.get(dataSetKey);
				progresBar.progressProperty().unbind();
				progresBar.setVisible(false);
				label.setText("Data sets constructed and saved");
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		});
		
		return constructCompleteDS;
	}

	// TODO: delete
	/*
	@FXML
	private void handleLoadImageFX() {
	
		fileChooser.setTitle("Choose image file");
		fileChooser.getExtensionFilters().clear();
		fileChooser.getExtensionFilters().add(
				new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jepg", "*.png", "*.bmp"));
		try {
	
			// open file
			File file = fileChooser.showOpenDialog(null);
			if (file != null) {
	
				Image image = new Image(file.toURI().toURL().toString(), true);
				imageView.setImage(image);
				fileChooser.setInitialDirectory(file.getParentFile());
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
	}
	*/
	
	private void sortFiles(File[] files) {
		
		Arrays.sort(files, new Comparator<File>() {
	
			@Override
			public int compare(File f1, File f2) {
				
				String name1 = f1.getName();
				int indexDash = name1.lastIndexOf('-');
				int indexDot = name1.lastIndexOf('.');
				String group1 = name1.substring(0, indexDash);
				int  position1 = Integer.parseInt(name1, indexDash + 1, indexDot, 10);
				
				String name2 = f2.getName();
				indexDash = name2.lastIndexOf('-');
				indexDot = name2.lastIndexOf('.');
				String group2 = name2.substring(0, indexDash);
				int  position2 = Integer.parseInt(name2, indexDash +1, indexDot, 10);
				
				int groupComp = group1.compareToIgnoreCase(group2);
				if (groupComp == 0)
					return Integer.compare(position1, position2);
				else
					return groupComp;
			}
		});
		
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

	// TODO: delete
	/*
	@FXML
	private void handleLoadImageFX() {
	
		fileChooser.setTitle("Choose image file");
		fileChooser.getExtensionFilters().clear();
		fileChooser.getExtensionFilters().add(
				new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jepg", "*.png", "*.bmp"));
		try {
	
			// open file
			File file = fileChooser.showOpenDialog(null);
			if (file != null) {
	
				Image image = new Image(file.toURI().toURL().toString(), true);
				imageView.setImage(image);
				fileChooser.setInitialDirectory(file.getParentFile());
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
	}
	*/
}
