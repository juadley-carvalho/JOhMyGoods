package org.cardGames;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Card {

    private int number;
    private Color color;
    private String name;
    private int value;
    private int cost;
    private Resource resource;
    private boolean sun;
    private Resource product;
    private int qtdRawResource1;
    private int qtdRawResource2;
    private Resource rawResource1;
    private Resource rawResource2;
    private Resource chainResource1;
    private Resource chainResource2;
    private BufferedImage image;
    private BufferedImage selectedImage;
    private boolean isSelected = false;

    public Card(int number, Color color, String name, int value, int cost, Resource resource, boolean sun,
                Resource product, int qtdRawResource1, int qtdRawResource2,
                Resource rawResource1, Resource rawResource2, Resource chainResource1, Resource chainResource2) {
        this.number = number;
        this.color = color;
        this.name = name;
        this.value = value;
        this.cost = cost;
        this.resource = resource;
        this.sun = sun;
        this.product = product;
        this.qtdRawResource1 = qtdRawResource1;
        this.qtdRawResource2 = qtdRawResource2;
        this.rawResource1 = rawResource1;
        this.rawResource2 = rawResource2;
        this.chainResource1 = chainResource1;
        this.chainResource2 = chainResource2;
        this.image = setImage();
        this.selectedImage = setSelectedImage(this.image);
    }

    public void info() {
        System.out.println("Card " + number + " " + color + " " + name + " " +
                            value + " " + cost + " " + resource + " " + sun + " " +
                            qtdRawResource1 + " " + rawResource1 + " " + qtdRawResource2 + " " +
                            rawResource2 + " " + chainResource1 + " " +
                            chainResource2 + " " + product + " " + selectedImage);
    }

    public BufferedImage setImage() {
        String imagePath = "src/main/resources/images/cards/carta_";
        BufferedImage image = null;
        try {
            image = ImageIO.read(new File(imagePath + String.format("%03d",number) + ".png"));
        } catch(IOException e) {
            System.out.println("Erro ao carregar imagem da carta: " + e.getMessage());
        }
        return image;
    }

    public BufferedImage setSelectedImage(BufferedImage image) { // Aplica filtro preto e branco na imagem da carta
        // Cria uma BufferedImage vazia em escala de cinza
        BufferedImage selectedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics graphics = selectedImage.getGraphics();
        // Desenha a imagem pixel a pixel com base na imagem original da carta
        graphics.drawImage(image.getScaledInstance(image.getWidth(), image.getHeight(), Image.SCALE_SMOOTH), 0, 0, null);
        graphics.dispose();
        return selectedImage;
    }

    public String getName(){ return name; }
    public BufferedImage getImage() { return image; }
    public BufferedImage getSelectedImage() { return selectedImage; }
    public boolean isSelected() { return isSelected; }
    public void toggleSelected() { isSelected = !isSelected; }
    public boolean isSun() { return sun; }
    public void setSelected(boolean selected) { this.isSelected = selected; }
}
