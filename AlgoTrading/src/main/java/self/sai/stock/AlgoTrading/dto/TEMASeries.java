package self.sai.stock.AlgoTrading.dto;

import java.time.LocalDateTime;

public class TEMASeries {
	LocalDateTime date;
	double closeAlt;
	double openAlt;
	double price;
	public LocalDateTime getDate() {
		return date;
	}
	public void setDate(LocalDateTime localDateTime) {
		this.date = localDateTime;
	}
	public double getCloseAlt() {
		return closeAlt;
	}
	public void setCloseAlt(double closeAlt) {
		this.closeAlt = closeAlt;
	}
	public double getOpenAlt() {
		return openAlt;
	}
	public void setOpenAlt(double openAlt) {
		this.openAlt = openAlt;
	}
	public double getPrice() {
		return price;
	}
	public void setPrice(double price) {
		this.price = price;
	}
	@Override
	public String toString() {
		return "date = "+ date.toString() + " closeTEMA = "+ closeAlt + " openTEMA = " +openAlt + " price = "+price;
	}
}
