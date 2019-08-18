package cpdh;

import java.io.File;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;



public class CpdhController {
	
	@FXML
	private ImageView imageView;
	@FXML
	private TextField textField;
	@FXML
	private TextArea textArea;
	@FXML
	private RadioButton rBtnOriginal, rBtnBinary, rBtnContour, rBtnPoints, rBtnCircle, rBtnRegions;
	@FXML
	private ToggleGroup toggleGroup;
	
	private final FileChooser fileChooser = new FileChooser();
	private final DirectoryChooser directoryChooser = new DirectoryChooser();
	
	private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

	private Cpdh cpdh;
	
	
	@FXML
	private void initialize() {

		
		toggleGroup.selectedToggleProperty().addListener( (observable, oldValue, newValue) -> {

			Image image = (Image) toggleGroup.getSelectedToggle().getUserData();
			imageView.setImage(image);
		});
	}
	
	@FXML
	 private void handleItemLoadImage() {
		
		fileChooser.setTitle("Choose image file to open");
		fileChooser.getExtensionFilters().clear();
		fileChooser.getExtensionFilters().add(
				new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jepg", "*.png", "*.bmp"));
		fileChooser.setInitialDirectory(new File(".\\pictures"));
		File file = fileChooser.showOpenDialog(imageView.getScene().getWindow());
		
		if (file != null) {
			
//			updateSkipPosition(file);
			
			Task<Cpdh> processFile = new Task<Cpdh>() {

				@Override
				protected Cpdh call() throws Exception {
					return new Cpdh(file);
				}
			};
			processFile.setOnSucceeded(event -> {

				try {
					Cpdh cpdh = processFile.get();
					configRadBtn(cpdh.getImages());
					textField.setText("");
					textArea.setText(cpdh.toString());
					this.cpdh = cpdh;
					//						TODO Enable save
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
			executorService.submit(processFile);
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
			if (toggleGroup.getSelectedToggle() != rBtnOriginal) { // same RadioButton instance.	
				toggleGroup.selectToggle(rBtnOriginal);
			}
			else {
				Image image = (Image) rBtnOriginal.getUserData();
				imageView.setImage(image);
			}
			rBtnOriginal.requestFocus();	
		}
}
