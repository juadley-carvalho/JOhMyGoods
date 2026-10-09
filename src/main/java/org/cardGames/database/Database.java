package org.cardGames.database;

import org.cardGames.Card;
import org.cardGames.Color;
import org.cardGames.Resource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {
    Connection connection;

    public Database() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:src/main/java/org/cardGames/database/ohmygoods.db");
        } catch (Exception e) {
            System.out.println("Connection Failed! Error: " + e);
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

    public List<Card> getCards() {
        List<Card> deck = new ArrayList<>();
        try(Statement stmt = this.connection.createStatement()) {
            ResultSet rs = stmt. executeQuery("SELECT * FROM CARTA");
            while(rs.next()) {
                if (rs.getString("CAR_RECURSO").equals("0")) {continue;}
                deck.add(new Card(rs.getInt("CAR_NUMERO_IMAGEM"),
                        Color.valueOf(rs.getString("CAR_COR")),
                        rs.getString("CAR_NOME"),
                        rs.getInt("CAR_PONTOS"),
                        rs.getInt("CAR_CUSTO"),
                        Resource.fromString(rs.getString("CAR_RECURSO")),
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
