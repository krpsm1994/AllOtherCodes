package self.sai.stock.AlgoTrading.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import self.sai.stock.AlgoTrading.entity.Candle;

import java.util.List;

public interface CandleRepository extends JpaRepository<Candle, Long> {

    List<Candle> findByTokenOrderByDateAsc(String token);
}
