package org.cardGames;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public String toString() {
        return name + " #" + number;
    }

    public BufferedImage setImage() {
        String imagePath = "/images/cards/carta_" + String.format("%03d", number) + ".png";
        BufferedImage image = null;
        try {
            URL url = Card.class.getResource(imagePath);
            if (url == null) throw new IOException("imagem não encontrada no classpath: " + imagePath);
            image = ImageIO.read(url);
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

    public int getNumber() { return number; }
    public Color getColor() { return color; }
    public String getName(){ return name; }
    /** Pontos de vitória do estabelecimento. */
    public int getPoints() { return value; }
    /** Custo em moedas para construir. */
    public int getCost() { return cost; }
    /** Recurso que a carta oferece no mercado ou na mão (null na Carvoaria). */
    public Resource getResource() { return resource; }
    /** Bem produzido pelo estabelecimento (null nas guildas que não produzem nada). */
    public Resource getProduct() { return product; }
    /** Valor em moedas de cada bem produzido (moeda no rodapé da carta). */
    public int getGoodValue() { return product == null ? 0 : product.getValue(); }

    /** Recursos exigidos para produzir, com a quantidade de cada um. */
    public Map<Resource, Integer> getRawResources() {
        Map<Resource, Integer> raw = new EnumMap<>(Resource.class);
        if (rawResource1 != null) raw.merge(rawResource1, qtdRawResource1, Integer::sum);
        if (rawResource2 != null) raw.merge(rawResource2, qtdRawResource2, Integer::sum);
        return Collections.unmodifiableMap(raw);
    }

    /** Itens da cadeia de produção (0, 1 ou 2), entregues juntos para gerar bens extras. */
    public List<Resource> getChainResources() {
        List<Resource> chain = new ArrayList<>(2);
        if (chainResource1 != null) chain.add(chainResource1);
        if (chainResource2 != null) chain.add(chainResource2);
        return List.copyOf(chain);
    }

    /** Guildas (cartas pretas) dão bônus, mas não recebem trabalhador nem produzem bens. */
    public boolean isProducer() { return color != Color.PRETO; }
    public boolean isGuild() { return color == Color.PRETO; }

    /** Recurso extra que a guilda dá ao dono para iniciar produções (null se não for guilda de recurso). */
    public Resource getGuildResource() { return isGuild() ? product : null; }

    /** Guilda que dá +1 carta na Fase I (guilda sem produto no banco). */
    public boolean isCardGuild() { return isGuild() && product == null; }

    public BufferedImage getImage() { return image; }
    public BufferedImage getSelectedImage() { return selectedImage; }
    public boolean isSelected() { return isSelected; }
    public void toggleSelected() { isSelected = !isSelected; }
    public boolean isSun() { return sun; }
    public void setSelected(boolean selected) { this.isSelected = selected; }
}
