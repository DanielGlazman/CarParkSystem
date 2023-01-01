package il.cshaifasweng.OCSFMediatorExample.client;

import javafx.application.Platform;
import javafx.scene.control.Alert;

public abstract class Controller {
    public static void sendAlert(String error, String title, Alert.AlertType type) {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(type);
                alert.setTitle(title);
                alert.setHeaderText(error);
                //alert.setContentText("Are you sure?");
                alert.showAndWait().get();
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        });
    }
}