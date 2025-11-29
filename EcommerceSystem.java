
import java.time.*;
import java.util.*;


public class EcommerceSystem {
    public static void main(String[] args) {
        Client client = new Client(1, "Айдана", "aidana@example.com", "Алматы, Абай 10", "+77001234567");
        Admin admin = new Admin(2, "Админ", "admin@example.com", "Офис", "+77009876543");

        Warehouse w1 = new Warehouse(1, "Негізгі қойма", "Алматы");
        Warehouse w2 = new Warehouse(2, "Қосымша қойма", "Шымкент");

        InventoryService inventory = new InventoryService();
        inventory.addWarehouse(w1);
        inventory.addWarehouse(w2);

        Product p1 = ProductFactory.createProduct(ProductType.PHYSICAL, 101, "Смартфон X", "Жаңа смартфон", 199_990.0, "Электроника", Arrays.asList("phone1.jpg"));
        Product p2 = ProductFactory.createProduct(ProductType.PHYSICAL, 102, "Құлаққап Wireless", "Сымсыз құлаққап", 12_990.0, Arrays.asList("hp1.jpg","hp2.jpg"), "Аксессуарлар");

        inventory.addStock(w1.getId(), p1, 15);
        inventory.addStock(w2.getId(), p2, 50);

        Cart cart = new Cart(client, inventory);
        cart.addProduct(p1, 1);
        cart.addProduct(p2, 2);

        PromoCode promo = new PromoCode("WELCOME10", PromoType.PERCENTAGE, 10.0, LocalDate.now().plusDays(30));
        cart.applyPromo(promo);

        ReviewService reviewService = new ReviewService();
        reviewService.addReview(p2, client, 5, "Тамаша дыбыс және ұзақ уақыт зарядталмайды.");

        Order order = cart.checkout();
        if (order == null) {
            System.out.println("Тапсырыс жасау мүмкін болмады (қор жеткіліксіз).");
            return;
        }
        order.setDelivery(new Delivery(201, client.getAddress(), DeliveryStatus.PENDING, null));

        Payment payment = new Payment(301, PaymentType.CARD, order.getTotal(), PaymentStatus.PENDING, LocalDateTime.now());
        PaymentProcessor paymentGateway = new DummyPaymentGateway();
        paymentGateway.process(payment);
        order.pay(payment);

        AdminActionLogger.logAction(admin, "Сатылымдар есептерін қарады");

        LoyaltyProgram.awardPoints(client, order);

        System.out.println("Тапсырыс id=" + order.getId() + ", жалпы: " + order.getTotal());
        System.out.println("Клиенттың баллы: " + client.getLoyaltyPoints());
        System.out.println("Қоймадағы өнімдер: p1 in w1 = " + inventory.getStock(w1.getId(), p1) + ", p2 in w2 = " + inventory.getStock(w2.getId(), p2));
    }
}

enum Role { CLIENT, ADMIN }
enum ProductType { PHYSICAL, DIGITAL, SERVICE }
enum PaymentType { CARD, E_WALLET }
enum PaymentStatus { PENDING, COMPLETED, FAILED, REFUNDED }
enum OrderStatus { CREATED, PROCESSING, SHIPPED, DELIVERED, CANCELLED }
enum DeliveryStatus { PENDING, IN_TRANSIT, DELIVERED, RETURNED }
enum PromoType { PERCENTAGE, FIXED }

abstract class Account {
    protected int id;
    protected String name;
    protected String email;

    public Account(int id, String name, String email) {
        this.id = id; this.name = name; this.email = email;
    }
    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }

    public abstract void updateData(Map<String,String> data);
}

abstract class User extends Account {
    protected String address;
    protected String phone;
    protected Role role;
    protected List<Order> orderHistory = new ArrayList<>();

    public User(int id, String name, String email, String address, String phone, Role role) {
        super(id, name, email);
        this.address = address; this.phone = phone; this.role = role;
    }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public void register() { /* тіркеу логикасы */ }
    public void login() { /* логин логикасы */ }
    public void addOrderToHistory(Order o) { orderHistory.add(o); }

    @Override
    public void updateData(Map<String,String> data) {
        if (data.containsKey("address")) this.address = data.get("address");
        if (data.containsKey("phone")) this.phone = data.get("phone");
    }
}

class Client extends User {
    private int loyaltyPoints = 0;

    public Client(int id, String name, String email, String address, String phone) {
        super(id,name,email,address,phone,Role.CLIENT);
    }

    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void addLoyaltyPoints(int pts) { loyaltyPoints += pts; }
}

class Admin extends User {
    public Admin(int id, String name, String email, String address, String phone) {
        super(id,name,email,address,phone,Role.ADMIN);
    }

    public void createProduct(Product p) { /* create */ }
    public void updateProduct(Product p) { /* update */ }
    public void deleteProduct(Product p) { /* delete */ }
}

class Product {
    private final int id;
    private String name;
    private String description;
    private double price;
    private String category;
    private List<String> images = new ArrayList<>();

    public Product(int id, String name, String description, double price, String category, List<String> images) {
        this.id = id; this.name = name; this.description = description; this.price = price; this.category = category;
        if (images != null) this.images = new ArrayList<>(images);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public String getCategory() { return category; }
    public List<String> getImages() { return Collections.unmodifiableList(images); }

    public void setPrice(double price) { this.price = price; }
    public void setDescription(String d) { this.description = d; }
    public void setName(String n) { this.name = n; }
}

class ProductFactory {
    public static Product createProduct(ProductType type, int id, String name, String desc, double price, List<String> images, String category) {
        return new Product(id, name, desc, price, category, images);
    }
    public static Product createProduct(ProductType type, int id, String name, String desc, double price, String category, List<String> images) {
        return new Product(id, name, desc, price, category, images);
    }
}

class Order {
    private final int id;
    private final LocalDateTime createdAt;
    private OrderStatus status;
    private final Client client;
    private final List<OrderItem> items = new ArrayList<>();
    private double total = 0.0;
    private Delivery delivery;
    private Payment payment;

    public Order(int id, Client client) {
        this.id = id; this.client = client; this.createdAt = LocalDateTime.now(); this.status = OrderStatus.CREATED;
    }

    public int getId() { return id; }
    public double getTotal() { return total; }
    public Client getClient() { return client; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public OrderStatus getStatus() { return status; }

    public void addItem(Product p, int qty) {
        items.add(new OrderItem(p, qty, p.getPrice()));
        recalcTotal();
    }

    private void recalcTotal() {
        total = items.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
    }

    public void place() { this.status = OrderStatus.PROCESSING; }
    public void cancel() { this.status = OrderStatus.CANCELLED; }
    public void setDelivery(Delivery d) { this.delivery = d; }
    public Delivery getDelivery() { return delivery; }

    public void pay(Payment payment) {
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            this.payment = payment;
            this.status = OrderStatus.PROCESSING;
            client.addOrderToHistory(this);
        } else {
            System.out.println("Төлем сәтсіз: " + payment.getId());
        }
    }
}

class OrderItem {
    private final Product product;
    private final int quantity;
    private final double price;

    public OrderItem(Product product, int quantity, double price) {
        this.product = product; this.quantity = quantity; this.price = price;
    }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
}

class Cart {
    private final Client client;
    private final Map<Product, Integer> products = new LinkedHashMap<>();
    private PromoCode appliedPromo = null;
    private final InventoryService inventory;

    public Cart(Client client, InventoryService inventory) {
        this.client = client;
        this.inventory = inventory;
    }

    public void addProduct(Product p, int qty) {
        if (qty <= 0) return;
        products.put(p, products.getOrDefault(p, 0) + qty);
    }

    public void removeProduct(Product p) {
        products.remove(p);
    }

    public void applyPromo(PromoCode promo) {
        if (promo != null && promo.isValid()) {
            appliedPromo = promo;
        } else {
            System.out.println("Промокод жарамсыз немесе мерзімі өткен.");
        }
    }

    public double calculateSubtotal() {
        double sum = 0;
        for (Map.Entry<Product, Integer> e : products.entrySet()) {
            sum += e.getKey().getPrice() * e.getValue();
        }
        return sum;
    }

    public double calculateTotal() {
        double subtotal = calculateSubtotal();
        if (appliedPromo != null) subtotal = appliedPromo.apply(subtotal);
        return Math.max(0, subtotal);
    }

     
    public Order checkout() {
        Map<Product, Integer> reserved = new HashMap<>();
        for (Map.Entry<Product,Integer> e : products.entrySet()) {
            Product p = e.getKey(); int qty = e.getValue();
            Optional<Integer> wid = inventory.reserveStock(p, qty);
            if (!wid.isPresent()) {
                for (Map.Entry<Product,Integer> r : reserved.entrySet()) {
                    inventory.unreserveStock(r.getKey(), r.getValue());
                }
                System.out.println("Қор жеткіліксіз: " + p.getName());
                return null;
            } else {
                reserved.put(p, qty);
            }
        }

        Order order = new Order(new Random().nextInt(900000)+1000, client);
        for (Map.Entry<Product,Integer> e : products.entrySet()) {
            order.addItem(e.getKey(), e.getValue());
        }

        double total = calculateTotal();
        System.out.println("Checkout subtotal: " + calculateSubtotal() + ", total after promo: " + total);

        order.place();
        return order;
    }
}

class PromoCode {
    private final String code;
    private final PromoType type;
    private final double value;
    private final LocalDate expiresAt;

    public PromoCode(String code, PromoType type, double value, LocalDate expiresAt) {
        this.code = code; this.type = type; this.value = value; this.expiresAt = expiresAt;
    }

    public String getCode() { return code; }
    public PromoType getType() { return type; }
    public double getValue() { return value; }

    public boolean isValid() {
        return !LocalDate.now().isAfter(expiresAt);
    }

    public double apply(double total) {
        if (!isValid()) return total;
        if (type == PromoType.PERCENTAGE) {
            return total * (1 - value / 100.0);
        } else {
            return Math.max(0, total - value);
        }
    }
}

class Payment {
    private final int id;
    private final PaymentType type;
    private final double amount;
    private PaymentStatus status;
    private final LocalDateTime date;

    public Payment(int id, PaymentType type, double amount, PaymentStatus status, LocalDateTime date) {
        this.id = id; this.type = type; this.amount = amount; this.status = status; this.date = date;
    }

    public int getId() { return id; }
    public PaymentType getType() { return type; }
    public double getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus s) { status = s; }
}

interface PaymentProcessor {
    void process(Payment payment);
    void refund(Payment payment);
}

class DummyPaymentGateway implements PaymentProcessor {
    @Override
    public void process(Payment payment) {
        payment.setStatus(PaymentStatus.COMPLETED);
        System.out.println("Төлем өңделді id=" + payment.getId() + ", amount=" + payment.getAmount());
    }

    @Override
    public void refund(Payment payment) {
        payment.setStatus(PaymentStatus.REFUNDED);
        System.out.println("Төлем қайтарылды id=" + payment.getId());
    }
}

class Delivery {
    private final int id;
    private final String address;
    private DeliveryStatus status;
    private Courier courier;

    public Delivery(int id, String address, DeliveryStatus status, Courier courier) {
        this.id = id; this.address = address; this.status = status; this.courier = courier;
    }

    public int getId() { return id; }
    public String getAddress() { return address; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus s) { status = s; }
    public Courier getCourier() { return courier; }
    public void setCourier(Courier c) { courier = c; }
}

class Courier {
    private final int id;
    private final String name;
    private final String phone;

    public Courier(int id, String name, String phone) { this.id = id; this.name = name; this.phone = phone; }
}

interface CourierServiceAPI {
    void createShipment(Order order);
    void trackShipment(int deliveryId);
}

class DummyCourierIntegration implements CourierServiceAPI {
    @Override
    public void createShipment(Order order) {
        System.out.println("Жеткізу: отправление oluşturuldu for order " + order.getId());
    }

    @Override
    public void trackShipment(int deliveryId) {
        System.out.println("Жеткізу статусы тексерілді: " + deliveryId);
    }
}

class Warehouse {
    private final int id;
    private final String name;
    private final String location;

    public Warehouse(int id, String name, String location) { this.id = id; this.name = name; this.location = location; }
    public int getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
}


class InventoryService {
    private final Map<Integer, Map<Integer, Integer>> stockByWarehouse = new HashMap<>();
    private final Map<Integer, Warehouse> warehouses = new HashMap<>();
    private final Map<Integer, Integer> reservedTotals = new HashMap<>();

    public void addWarehouse(Warehouse w) {
        warehouses.put(w.getId(), w);
        stockByWarehouse.put(w.getId(), new HashMap<>());
    }

    public void addStock(int warehouseId, Product p, int qty) {
        stockByWarehouse.get(warehouseId).put(p.getId(), stockByWarehouse.get(warehouseId).getOrDefault(p.getId(), 0) + qty);
    }

    public int getStock(int warehouseId, Product p) {
        return stockByWarehouse.getOrDefault(warehouseId, Collections.emptyMap()).getOrDefault(p.getId(), 0);
    }

    
    public Optional<Integer> reserveStock(Product p, int qty) {
        for (Integer wid : stockByWarehouse.keySet()) {
            int available = stockByWarehouse.get(wid).getOrDefault(p.getId(), 0);
            if (available >= qty) {
                stockByWarehouse.get(wid).put(p.getId(), available - qty);
                reservedTotals.put(p.getId(), reservedTotals.getOrDefault(p.getId(), 0) + qty);
                return Optional.of(wid);
            }
        }
        return Optional.empty();
    }

    
    public void unreserveStock(Product p, int qty) {
        for (Integer wid : stockByWarehouse.keySet()) {
            stockByWarehouse.get(wid).put(p.getId(), stockByWarehouse.get(wid).getOrDefault(p.getId(), 0) + qty);
            reservedTotals.put(p.getId(), Math.max(0, reservedTotals.getOrDefault(p.getId(), 0) - qty));
            return;
        }
    }
}

class Review {
    private final int id;
    private final Product product;
    private final Client client;
    private final int rating; // 1..5
    private final String text;
    private final LocalDateTime date;

    public Review(int id, Product product, Client client, int rating, String text) {
        this.id = id; this.product = product; this.client = client; this.rating = rating; this.text = text; this.date = LocalDateTime.now();
    }

    public int getRating() { return rating; }
    public String getText() { return text; }
    public Client getClient() { return client; }
    public Product getProduct() { return product; }
}

class ReviewService {
    private final List<Review> reviews = new ArrayList<>();
    private int nextId = 1;

    public Review addReview(Product p, Client c, int rating, String text) {
        Review r = new Review(nextId++, p, c, rating, text);
        reviews.add(r);
        return r;
    }

    public List<Review> getReviewsForProduct(Product p) {
        List<Review> out = new ArrayList<>();
        for (Review r : reviews) if (r.getProduct().getId() == p.getId()) out.add(r);
        return out;
    }

    public double averageRating(Product p) {
        List<Review> list = getReviewsForProduct(p);
        if (list.isEmpty()) return 0.0;
        return list.stream().mapToDouble(Review::getRating).average().orElse(0.0);
    }
}

class AdminActionLogger {
    public static void logAction(Admin admin, String action) {
        // Нақты жүйеде бұл жазба DB-ге немесе лог-сервиске кетеді.
        System.out.println("[ADMIN LOG] " + LocalDateTime.now() + " adminId=" + admin.getId() + " action=\"" + action + "\"");
    }
}

class LoyaltyProgram {
    public static void awardPoints(Client client, Order order) {
        int points = (int)Math.floor(order.getTotal() / 10000.0); // мысал: 10000 теңгеге 1 балл
        client.addLoyaltyPoints(points);
        System.out.println("Loyalty: клиентқа " + points + " балл берілді.");
    }
}

class AnalyticsService {
    public void generateReport() {
        System.out.println("Аналитика есебі дайындалды (демо).");
    }
}
