package Client;


import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GalleryView extends JFrame implements ActionListener {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 256;

    private HttpClient client;
    private String key;

    JScrollPane panel = new JScrollPane();
    JButton deleteButton = new JButton("DELETE");
    JButton uploadButton = new JButton("UPLOAD");
    JButton browseButton = new JButton("BROWSE");
    JButton addButton = new JButton("ADD USER");
    DefaultListModel dm = new DefaultListModel();
    JList jList = new JList();

    private List<ImageIcon> imageIcons = new ArrayList<>();


    public GalleryView(String key) throws HeadlessException{
        this.key = key;
        this.client = HttpClient
                .newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(ProxySelector.of(new InetSocketAddress(8080)))
                .build();
        getImageIcons();
        addAction();
        jList.setModel(dm);
        dm.addAll(imageIcons);
        setLocationAndSize();
        panel.setViewportView(jList);
        add(uploadButton);
        add(deleteButton);
        add(browseButton);
        add(addButton);
        add(panel);
    }

    private void setLocationAndSize(){
        deleteButton.setBounds(350, 50, 100, 30);
        uploadButton.setBounds(350, 100, 100, 30);
        browseButton.setBounds(350, 150, 100, 30);
        addButton.setBounds(350, 200, 100, 30);
        jList.setBounds(5, 5, WIDTH + 25, imageIcons.size() * HEIGHT + 10);
    }
    private void addAction(){
        this.deleteButton.addActionListener(this);
        this.uploadButton.addActionListener(this);
        browseButton.addActionListener(this);
        addButton.addActionListener(this);
    }

    private List<byte[]> getFilesInBytes(){
        List<byte[]> result = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8080/getImages"))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(key))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            Integer n = ByteBuffer.wrap(response.body()).getInt();
            for (Integer i = 0; i < n; i++) {
                HttpRequest tmpReq = HttpRequest.newBuilder()
                        .uri(new URI("http://localhost:8080/getSingleImage?i=" + i.toString()))
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .GET()
                        .build();
                HttpResponse<byte[]> tmpResponse = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .proxy(ProxySelector.of(new InetSocketAddress(8080)))
                        .build()
                        .send(tmpReq, HttpResponse.BodyHandlers.ofByteArray());
                result.add(tmpResponse.body());
            }
        } catch (URISyntaxException | IOException | InterruptedException e){
            System.err.println(e);
        }
        return result;
    }

    private void getImageIcons(){
        for(byte[] i: getFilesInBytes()){
            ByteArrayInputStream bais = new ByteArrayInputStream(i);
            try {
                BufferedImage img = ImageIO.read(bais);
                imageIcons.add(new ImageIcon(changeImageSize(img)));
            } catch (IOException ex){
                System.err.println(ex);
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(e.getSource() == deleteButton){
            proceedDelete();
        }
        else if(e.getSource() == uploadButton){
            proceedUpload();
        }
        else if(e.getSource() == browseButton){
            proceedBrowse();
        }
        else if(e.getSource() == addButton){
            proceedAdd();
        }
    }

    private void proceedDelete(){
        Integer index = jList.getSelectedIndex();
        if(index == -1){
            return;
        }
        dm.remove(index);
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8080/deleteImage?index=" + index.toString()))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("Token", key)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());
        } catch (Exception ex){
            System.err.println(ex);
        }
    }

    private void proceedUpload() {
        JFileChooser jf = new JFileChooser();
        int returnVal = jf.showDialog(this, "Upload");
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = jf.getSelectedFile();
        int index = file.toString().lastIndexOf('.');
        String extension = null;
        if (index > 0) {
            extension = file.toString().substring(index + 1);
        }
        else{
            return;
        }
        if(!extension.equals("jpg") && !extension.equals("png")){
            System.out.println("Bad format");
            return;
        }
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8080/sendImage"))
                    .header("Content-Type", "image/" + extension)
                    .POST(HttpRequest.BodyPublishers.ofFile(file.toPath()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());
            imageIcons.add(new ImageIcon(changeImageSize(ImageIO.read(file))));
            dm.add(imageIcons.size() - 1, imageIcons.get(imageIcons.size() - 1));
        } catch (IOException | URISyntaxException | InterruptedException ex){
            System.err.println(ex);
        }
    }

    private void proceedBrowse(){
        new BrowseUsersView(key);
    }

    private void proceedAdd(){
        new AddUserView(key);
    }


    private BufferedImage changeImageSize(BufferedImage in){
        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, in.getType());
        Graphics2D g2d = out.createGraphics();
        g2d.drawImage(in, 0, 0, WIDTH, HEIGHT, null);
        g2d.dispose();
        return out;
    }
}