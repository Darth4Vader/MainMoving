package JavaFXInterface;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileSystemView;

import DataStructures.FolderInfo;
import DataStructures.ManageFolder;
import FileUtilities.FilesUtils;
import OtherUtilities.ImageUtils;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Cell;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeTableCell;
import javafx.scene.image.WritableImage;

public class AppUtils {

	public static WritableImage getImageOfFile(File file) {
		File imageFile = null;
		if(file.isDirectory())
			imageFile = FilesUtils.getFileLogo(file);
		java.awt.Image image = null;
		System.out.println(file + " imageFile: " + imageFile);
		/*if(imageFile != null)
			image = ImageUtils.loadImage(imageFile.getPath());
		else {*/
			Icon icon = FileSystemView.getFileSystemView().getSystemIcon(file);
			System.out.println(file + " OIcon: " + icon);
			if(icon instanceof ImageIcon) {
				image = ImageUtils.toBufferedImage(((ImageIcon) icon).getImage());
			}
			else if(icon != null) { 
				image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = ((BufferedImage) image).createGraphics(); icon.paintIcon(null, g, 0, 0);
				g.dispose();
			}
		//}
		//System.out.println(image.getClass());
		if(image instanceof BufferedImage)
			return SwingFXUtils.toFXImage((BufferedImage) image, null);
		return null;
	}
	
	public static Control getCellOwner(Cell<?> cell) {
		if(cell instanceof ListCell)
			return ((ListCell<?>) cell).getListView();
		if(cell instanceof TableCell)
			return ((TableCell<?,?>) cell).getTableView();
		if(cell instanceof TreeTableCell)
			return ((TreeTableCell<?,?>) cell).getTreeTableView();
		if(cell instanceof TreeCell)
			return ((TreeCell<?>) cell).getTreeView();
		return null;
	}

}
