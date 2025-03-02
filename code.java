import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.json.JSONArray;
import org.json.JSONObject;

public class DailyExpenseTracker {

    private static final String EXPENSES_FILE = "expenses.json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static List<Expense> expenses = new ArrayList<>();

    static class Expense {
        LocalDate date;
        String description;
        double amount;

        public Expense(LocalDate date, String description, double amount) {
            this.date = date;
            this.description = description;
            this.amount = amount;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("date", date.format(DATE_FORMATTER));
            json.put("description", description);
            json.put("amount", amount);
            return json;
        }

        public static Expense fromJson(JSONObject json) {
            LocalDate date = LocalDate.parse(json.getString("date"), DATE_FORMATTER);
            String description = json.getString("description");
            double amount = json.getDouble("amount");
            return new Expense(date, description, amount);
        }
    }

    static class ExpensesHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String response = "";
            int responseCode = 200;

            try {
                if ("GET".equals(method)) {
                    response = getExpensesJson().toString();
                } else if ("POST".equals(method)) {
                    String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody())).lines()
                            .collect(Collectors.joining("\n"));
                    JSONObject json = new JSONObject(requestBody);
                    addExpense(Expense.fromJson(json));
                    response = "Expense added successfully";
                } else if ("DELETE".equals(method)) {
                    String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody())).lines()
                            .collect(Collectors.joining("\n"));
                    JSONObject json = new JSONObject(requestBody);
                    removeExpense(LocalDate.parse(json.getString("date"), DATE_FORMATTER), json.getString("description"), json.getDouble("amount"));
                    response = "Expense deleted successfully";
                } else {
                    response = "Method not supported";
                    responseCode = 405;
                }
            } catch (Exception e) {
                response = "Error: " + e.getMessage();
                responseCode = 500;
                e.printStackTrace();
            }

            exchange.sendResponseHeaders(responseCode, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    public static void main(String[] args) throws IOException {
        loadExpenses();
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress(8080), 0);
        server.createContext("/expenses", new ExpensesHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Server started on port 8080");
    }

    private static void loadExpenses() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(EXPENSES_FILE)));
            JSONArray jsonArray = new JSONArray(content);
            for (int i = 0; i < jsonArray.length(); i++) {
                expenses.add(Expense.fromJson(jsonArray.getJSONObject(i)));
            }
        } catch (IOException e) {
            System.out.println("Expenses file not found. Creating new file.");
            saveExpenses();
        } catch (org.json.JSONException e2){
            System.out.println("Expenses file corrupt, overwriting");
            expenses.clear();
            saveExpenses();
        }

    }

    private static void saveExpenses() {
        JSONArray jsonArray = new JSONArray();
        for (Expense expense : expenses) {
            jsonArray.put(expense.toJson());
        }
        try (PrintWriter out = new PrintWriter(EXPENSES_FILE)) {
            out.print(jsonArray.toString(4));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static JSONArray getExpensesJson() {
        JSONArray jsonArray = new JSONArray();
        for (Expense expense : expenses) {
            jsonArray.put(expense.toJson());
        }
        return jsonArray;
    }

    private static void addExpense(Expense expense) {
        expenses.add(expense);
        saveExpenses();
    }

    private static void removeExpense(LocalDate date, String description, double amount) {
        expenses.removeIf(e -> e.date.equals(date) && e.description.equals(description) && e.amount == amount);
        saveExpenses();
    }
}
