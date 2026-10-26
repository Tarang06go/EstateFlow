package com.estateflow.dao;
import com.estateflow.model.*;import java.util.List;import java.util.Optional;
public interface VendorDao {boolean isAvailable();Vendor insert(Vendor v);int update(Vendor v);Optional<Vendor> find(long id);List<Vendor> search(String text,boolean includeInactive);}
