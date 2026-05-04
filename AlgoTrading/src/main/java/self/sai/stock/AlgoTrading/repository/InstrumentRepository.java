package self.sai.stock.AlgoTrading.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import self.sai.stock.AlgoTrading.entity.Instrument;

import java.util.List;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    List<Instrument> findByExchangeIgnoreCase(String exchange);

    List<Instrument> findByType(String type);
}
