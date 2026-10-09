package org.cardGames.database;

import org.cardGames.Assistant;
import org.cardGames.Card;
import org.cardGames.Color;
import org.cardGames.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private static final String RESOURCE = "/ohmygoods.db";

    Connection connection;

    public Database() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + extractDatabase());
        } catch (Exception e) {
            System.out.println("Connection Failed! Error: " + e);
        }
    }

    /** O SQLite não lê arquivos de dentro do .jar: copia o banco do classpath para um arquivo temporário. */
    private static Path extractDatabase() throws IOException {
        try (InputStream in = Database.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("banco não encontrado no classpath: " + RESOURCE);
            Path tmp = Files.createTempFile("ohmygoods", ".db");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void closeConnection() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception e) {
            System.out.println("Error closing connection: " + e);
        }
    }

    /** Cartas do baralho: estabelecimentos e guildas (as Carvoarias ficam de fora). */
    public List<Card> getCards() {
        return queryCards("SELECT * FROM CARTA WHERE CAR_RECURSO <> '0'");
    }

    /** As 4 Carvoarias: cada jogador começa com uma. Não têm recurso (CAR_RECURSO = '0'). */
    public List<Card> getCharcoalBurners() {
        return queryCards("SELECT * FROM CARTA WHERE CAR_RECURSO = '0'");
    }

    private List<Card> queryCards(String sql) {
        List<Card> deck = new ArrayList<>();
        try(Statement stmt = this.connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while(rs.next()) {
                String resource = rs.getString("CAR_RECURSO");
                deck.add(new Card(rs.getInt("CAR_NUMERO_IMAGEM"),
                        Color.valueOf(rs.getString("CAR_COR")),
                        rs.getString("CAR_NOME"),
                        rs.getInt("CAR_PONTOS"),
                        rs.getInt("CAR_CUSTO"),
                        "0".equals(resource) ? null : Resource.fromString(resource),
                        (rs.getInt("CAR_SOL") == 1),
                        Resource.fromString(rs.getString("CAR_PRODUTO")),
                        rs.getInt("CAR_QUANTIDADE_PROD_1"),
                        rs.getInt("CAR_QUANTIDADE_PROD_2"),
                        Resource.fromString(rs.getString("CAR_RECURSO_PROD_1")),
                        Resource.fromString(rs.getString("CAR_RECURSO_PROD_2")),
                        Resource.fromString(rs.getString("CAR_RECURSO_CADEIA_1")),
                        Resource.fromString(rs.getString("CAR_RECURSO_CADEIA_2"))));
            }
        } catch (SQLException e) {
            System.out.println("Error getting cards: " + e);
        }
        return deck;
    }

    /** Os 16 assistentes (tabela AJUDANTE). Colunas de cor vazias são ignoradas. */
    public List<Assistant> getAssistants() {
        List<Assistant> assistants = new ArrayList<>();
        try(Statement stmt = this.connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM AJUDANTE ORDER BY AJU_NUMERO");
            while (rs.next()) {
                List<Color> colors = new ArrayList<>();
                for (int i = 1; i <= 5; i++) {
                    String color = rs.getString("AJU_COR_" + i);
                    if (color != null && !color.isBlank()) colors.add(Color.valueOf(color.trim()));
                }
                assistants.add(new Assistant(rs.getInt("AJU_NUMERO"), rs.getInt("AJU_CUSTO"),
                        rs.getInt("AJU_PONTOS"), colors));
            }
        } catch (SQLException e) {
            System.out.println("Error getting assistants: " + e);
        }
        return assistants;
    }

    public List<String> getBuildingsNames() {
        List<String> buildingsNames = new ArrayList<>();

        try(Statement stmt = this.connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT CAR_NOME FROM CARTA");
            while (rs.next()) {
                buildingsNames.add(rs.getString("CAR_NOME"));
            }
        } catch (SQLException e) {
            System.out.println("Error getting buildingsNames: " + e);
        }
        return buildingsNames;
    }
}
