package com.nokku.model;
import java.io.Serializable;
public class Product implements Serializable {

    private String name;
    private double price;
    private double rating;
    private String source;
    private String link;
    private double trustScore;
    private String image;
    private Integer deliveryDays;   //  / online
    private Double distanceKm;      //  / local
    // 🔥 New fields
    private int reviewCount;
    private String deliveryType; // SAME_DAY / NEXT_DAY / STANDARD

    // 🔥 Constructor
    public Product(String name, double price, double rating, String source, String link, String image) {
        this.name = name;
        this.price = price;
        this.rating = rating;
        this.source = source;
        this.link = link;
        this.trustScore = calculateTrust(source);
        if (image != null && !image.isEmpty()) {
            this.image = image;
        }
        // 🔥 Default values (for now)
        this.reviewCount = 100;
        this.deliveryType = "STANDARD";

    }

    public Product() {
        
    }

    // 🔥 Trust logic
    private double calculateTrust(String source) {
        if (source == null) return 0.7;

        if (source.equalsIgnoreCase("amazon")) return 0.9;
        if (source.equalsIgnoreCase("walmart")) return 0.8;
        if (source.equalsIgnoreCase("ebay")) return 0.75;
        if (source.equalsIgnoreCase("google-shopping")) return 0.85;

        return 0.7;
    }

    // 🔥 Getters
    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public double getRating() {
        return rating;
    }

    public String getSource() {
        return source;
    }

    public String getLink() {
        return link;
    }

    public double getTrustScore() {
        return trustScore;
    }
    public String getImage() {
        return image;
    }
    public int getReviewCount() {
        return reviewCount;
    }

    public String getDeliveryType() {
        return deliveryType;
    }
    public Integer getDeliveryDays() {
        return deliveryDays;
    }

    public void setDeliveryDays(Integer deliveryDays) {
        this.deliveryDays = deliveryDays;
    }
    // 🔥 Optional setters (future use)
    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }
   /* public void setImage(String image) {
        this.image = image;
    }*/
   public void setImage(String image) {
       if (image != null && !image.isEmpty()) {
           this.image = image;
       }
   }
    public void setRating(double rating) {
        this.rating = rating;
    }
    public void setDeliveryType(String deliveryType) {
        this.deliveryType = deliveryType;
    }

    // 🔥 Debug helper
    @Override
    public String toString() {
        return "Product{" +
                "name='" + name + '\'' +
                ", price=" + price +
                ", rating=" + rating +
                ", source='" + source + '\'' +
                ", trustScore=" + trustScore +
                ", reviewCount=" + reviewCount +
                ", deliveryType='" + deliveryType + '\'' +
                '}';
    }


}