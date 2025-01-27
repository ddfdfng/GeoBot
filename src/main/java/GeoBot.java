import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class GeoBot {

    private static final int REQUEST_LIMIT = 10; // 10 запитів
    private static final long TIME_WINDOW = 24 * 60 * 60 * 1000;

    private static String selectedCategory = "cafe";
    private static final Map<Long, List<Long>> userRequests = new HashMap<>();

    public static void main(String[] args) {
        String telegramToken = System.getenv("8136864127:AAHjcrw_Q3kEan76wkc2it4Xq5YDNMuXvwA");
        if (telegramToken == null || telegramToken.isEmpty()) {
            throw new IllegalStateException("Telegram token is not set in environment variables.");
        }

        TelegramBot bot = new TelegramBot(telegramToken);

        bot.setUpdatesListener(updates -> {
            updates.forEach(update -> {
                if (update.message() != null) {
                    Long chatId = update.message().chat().id();

                    if (!canSendRequest(chatId)) {
                        bot.execute(new SendMessage(chatId, "Ви досягли ліміту запитів. Спробуйте пізніше."));
                        return;
                    }

                    if (update.message().location() != null) {
                        double userLat = update.message().location().latitude();
                        double userLon = update.message().location().longitude();
                        getPlaces(userLat, userLon, bot, chatId);
                    } else if (update.message().text() != null) {
                        handleTextMessage(update, bot);
                    }
                }
            });
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private static boolean canSendRequest(Long chatId) {
        long currentTime = System.currentTimeMillis();
        userRequests.putIfAbsent(chatId, new ArrayList<>());

        List<Long> requests = userRequests.get(chatId);


        requests.removeIf(timestamp -> timestamp < (currentTime - TIME_WINDOW));

        if (requests.size() >= REQUEST_LIMIT) {
            return false;
        }


        requests.add(currentTime);
        return true;
    }

    private static void handleTextMessage(Update update, TelegramBot bot) {
        String text = update.message().text();
        Long chatId = update.message().chat().id();

        switch (text) {
            case "Кафе":
                selectedCategory = "cafe";
                bot.execute(new SendMessage(chatId, "Ти вибрав категорію: Кафе. Тепер надішли свою геолокацію."));
                break;
            case "Ресторани":
                selectedCategory = "restaurant";
                bot.execute(new SendMessage(chatId, "Ти вибрав категорію: Ресторани. Тепер надішли свою геолокацію."));
                break;
            case "Аптеки":
                selectedCategory = "pharmacy";
                bot.execute(new SendMessage(chatId, "Ти вибрав категорію: Аптеки. Тепер надішли свою геолокацію."));
                break;
            case "Супермаркети":
                selectedCategory = "supermarket";
                bot.execute(new SendMessage(chatId, "Ти вибрав категорію: Супермаркети. Тепер надішли свою геолокацію."));
                break;
            case "СТО":
                selectedCategory = "car_repair";
                bot.execute(new SendMessage(chatId, "Ти вибрав категорію: СТО. Тепер надішли свою геолокацію."));
                break;
            case "Побут. техніка":
                selectedCategory = "electronics";
                bot.execute(new SendMessage(chatId, "Ти вибрав категорію: Побутова техніка. Тепер надішли свою геолокацію."));
                break;
            case "Красиві види":
                selectedCategory = "beautiful_views";
                bot.execute(new SendMessage(chatId, "Ти вибрав категорію: Красиві види. Тепер надішли свою геолокацію."));
                break;
            case "Розваги":
                selectedCategory = "entertainment";
                bot.execute(new SendMessage(chatId, "Ти вибрав категорію: Розваги. Тепер надішли свою геолокацію."));
                break;
            default:
                ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup(
                        new KeyboardButton("Кафе"),
                        new KeyboardButton("Ресторани"),
                        new KeyboardButton("Аптеки"),
                        new KeyboardButton("Супермаркети"),
                        new KeyboardButton("СТО"),
                        new KeyboardButton("Побут. техніка"),
                        new KeyboardButton("Красиві види"),
                        new KeyboardButton("Розваги")
                ).resizeKeyboard(true);

                bot.execute(new SendMessage(chatId, "Оберіть категорію:").replyMarkup(keyboard));
        }
    }

    private static void getPlaces(double userLat, double userLon, TelegramBot bot, long chatId) {
        String overpassUrl;


        switch (selectedCategory) {
            case "cafe":
            case "restaurant":
            case "pharmacy":
                overpassUrl = String.format(
                        "https://overpass-api.de/api/interpreter?data=[out:json];node[amenity=%s](around:1000,%f,%f);out;",
                        selectedCategory, userLat, userLon);
                break;
            case "supermarket":
                overpassUrl = String.format(
                        "https://overpass-api.de/api/interpreter?data=[out:json];node[shop=supermarket](around:1000,%f,%f);out;",
                        userLat, userLon);
                break;
            case "car_repair":
                overpassUrl = String.format(
                        "https://overpass-api.de/api/interpreter?data=[out:json];" +
                                "(node[amenity=car_repair](around:1000,%f,%f);" +
                                " node[shop=car_repair](around:1000,%f,%f));out;",
                        userLat, userLon, userLat, userLon);
                break;
            case "electronics":
                overpassUrl = String.format(
                        "https://overpass-api.de/api/interpreter?data=[out:json];node[shop=electronics](around:1000,%f,%f);out;",
                        userLat, userLon);
                break;
            case "beautiful_views":
                overpassUrl = String.format(
                        "https://overpass-api.de/api/interpreter?data=[out:json];node[tourism=viewpoint](around:1000,%f,%f);out;",
                        userLat, userLon);
                break;
            case "entertainment":
                overpassUrl = String.format(
                        "https://overpass-api.de/api/interpreter?data=[out:json];node[leisure=park](around:1000,%f,%f);out;",
                        userLat, userLon);
                break;
            default:
                bot.execute(new SendMessage(chatId, "Категорія не підтримується."));
                return;
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(overpassUrl)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                parseResponse(response.body().string(), userLat, userLon, bot, chatId);
            } else {
                bot.execute(new SendMessage(chatId, "Не вдалося отримати дані. Спробуй ще раз."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            bot.execute(new SendMessage(chatId, "Сталася помилка при отриманні даних."));
        }
    }

    private static void parseResponse(String json, double userLat, double userLon, TelegramBot bot, long chatId) {
        try {
            List<Place> places = new ArrayList<>();
            JSONObject jsonObject = new JSONObject(json);
            JSONArray elements = jsonObject.optJSONArray("elements");


            if (elements == null || elements.isEmpty()) {
                bot.execute(new SendMessage(chatId, "Нажаль, місць подібних до вашого запиту не було знайдено."));
                return;
            }


            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);
                JSONObject tags = element.optJSONObject("tags");

                if (tags != null) {
                    String name = tags.optString("name", "Без назви");
                    double lat = element.getDouble("lat");
                    double lon = element.getDouble("lon");


                    double distance = calculateDistance(userLat, userLon, lat, lon);
                    places.add(new Place(name, lat, lon, distance));
                }
            }


            if (places.isEmpty()) {
                bot.execute(new SendMessage(chatId, "Нажаль, місць подібних до вашого запиту не було знайдено."));
                return;
            }

            Collections.sort(places);

            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
            for (Place place : places) {
                String googleMapsLink = String.format(
                        "https://www.google.com/maps/dir/?api=1&origin=%f,%f&destination=%f,%f",
                        userLat, userLon, place.lat, place.lon);

                String buttonText = String.format("%s (%d м)", place.name, (int) place.distance);
                InlineKeyboardButton button = new InlineKeyboardButton(buttonText).url(googleMapsLink);
                inlineKeyboardMarkup.addRow(button);
            }

            bot.execute(new SendMessage(chatId, "Місця поруч (за відстанню):").replyMarkup(inlineKeyboardMarkup));
        } catch (Exception e) {
            e.printStackTrace();
            bot.execute(new SendMessage(chatId, "Сталася помилка при обробці даних."));
        }
    }


    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371000; // Радіус Землі в метрах
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    static class Place implements Comparable<Place> {
        String name;
        double lat;
        double lon;
        double distance;

        Place(String name, double lat, double lon, double distance) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.distance = distance;
        }

        @Override
        public int compareTo(Place other) {
            return Double.compare(this.distance, other.distance);
        }
    }
}
