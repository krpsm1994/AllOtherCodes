package self.sai.stock.AlgoTrading.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import self.sai.stock.AlgoTrading.entity.Trade;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    /** Returns distinct (token, name) pairs present in the trades table. */
    @Query("SELECT DISTINCT t.token, t.name FROM Trade t")
    List<Object[]> findDistinctTokenAndName();

    /** Returns distinct (token, name) pairs for trades with an active status. */
    @Query("SELECT DISTINCT t.token, t.name FROM Trade t WHERE t.status IN :statuses")
    List<Object[]> findDistinctTokenAndNameByStatusIn(@org.springframework.data.repository.query.Param("statuses") List<String> statuses);

    /** Returns all trades whose status is in the given list. */
    List<Trade> findByStatusIn(List<String> statuses);
}
