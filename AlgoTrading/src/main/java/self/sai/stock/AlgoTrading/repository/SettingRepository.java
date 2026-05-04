package self.sai.stock.AlgoTrading.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import self.sai.stock.AlgoTrading.entity.Setting;

import java.util.Optional;
import java.util.List;

public interface SettingRepository extends JpaRepository<Setting, Long> {
    Optional<Setting> findByKeyAndGroupName(String key, String groupName);
    List<Setting> findByGroupName(String groupName);
}
