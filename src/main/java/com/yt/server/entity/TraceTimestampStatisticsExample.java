package com.yt.server.entity;

import java.util.ArrayList;
import java.util.List;

public class TraceTimestampStatisticsExample {
    protected String orderByClause;

    protected boolean distinct;

    protected List<Criteria> oredCriteria;

    public TraceTimestampStatisticsExample() {
        oredCriteria = new ArrayList<Criteria>();
    }

    public void setOrderByClause(String orderByClause) {
        this.orderByClause = orderByClause;
    }

    public String getOrderByClause() {
        return orderByClause;
    }

    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public List<Criteria> getOredCriteria() {
        return oredCriteria;
    }

    public void or(Criteria criteria) {
        oredCriteria.add(criteria);
    }

    public Criteria or() {
        Criteria criteria = createCriteriaInternal();
        oredCriteria.add(criteria);
        return criteria;
    }

    public Criteria createCriteria() {
        Criteria criteria = createCriteriaInternal();
        if (oredCriteria.size() == 0) {
            oredCriteria.add(criteria);
        }
        return criteria;
    }

    protected Criteria createCriteriaInternal() {
        Criteria criteria = new Criteria();
        return criteria;
    }

    public void clear() {
        oredCriteria.clear();
        orderByClause = null;
        distinct = false;
    }

    protected abstract static class GeneratedCriteria {
        protected List<Criterion> criteria;

        protected GeneratedCriteria() {
            super();
            criteria = new ArrayList<Criterion>();
        }

        public boolean isValid() {
            return criteria.size() > 0;
        }

        public List<Criterion> getAllCriteria() {
            return criteria;
        }

        public List<Criterion> getCriteria() {
            return criteria;
        }

        protected void addCriterion(String condition) {
            if (condition == null) {
                throw new RuntimeException("Value for condition cannot be null");
            }
            criteria.add(new Criterion(condition));
        }

        protected void addCriterion(String condition, Object value, String property) {
            if (value == null) {
                throw new RuntimeException("Value for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value));
        }

        protected void addCriterion(String condition, Object value1, Object value2, String property) {
            if (value1 == null || value2 == null) {
                throw new RuntimeException("Between values for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value1, value2));
        }

        public Criteria andTraceIdIsNull() {
            addCriterion("traceId is null");
            return (Criteria) this;
        }

        public Criteria andTraceIdIsNotNull() {
            addCriterion("traceId is not null");
            return (Criteria) this;
        }

        public Criteria andTraceIdEqualTo(Long value) {
            addCriterion("traceId =", value, "traceId");
            return (Criteria) this;
        }

        public Criteria andTraceIdNotEqualTo(Long value) {
            addCriterion("traceId <>", value, "traceId");
            return (Criteria) this;
        }

        public Criteria andTraceIdGreaterThan(Long value) {
            addCriterion("traceId >", value, "traceId");
            return (Criteria) this;
        }

        public Criteria andTraceIdGreaterThanOrEqualTo(Long value) {
            addCriterion("traceId >=", value, "traceId");
            return (Criteria) this;
        }

        public Criteria andTraceIdLessThan(Long value) {
            addCriterion("traceId <", value, "traceId");
            return (Criteria) this;
        }

        public Criteria andTraceIdLessThanOrEqualTo(Long value) {
            addCriterion("traceId <=", value, "traceId");
            return (Criteria) this;
        }

        public Criteria andTraceIdIn(List<Long> values) {
            addCriterion("traceId in", values, "traceId");
            return (Criteria) this;
        }

        public Criteria andTraceIdNotIn(List<Long> values) {
            addCriterion("traceId not in", values, "traceId");
            return (Criteria) this;
        }

        public Criteria andTraceIdBetween(Long value1, Long value2) {
            addCriterion("traceId between", value1, value2, "traceId");
            return (Criteria) this;
        }

        public Criteria andTraceIdNotBetween(Long value1, Long value2) {
            addCriterion("traceId not between", value1, value2, "traceId");
            return (Criteria) this;
        }

        public Criteria andTempTimestampIsNull() {
            addCriterion("tempTimestamp is null");
            return (Criteria) this;
        }

        public Criteria andTempTimestampIsNotNull() {
            addCriterion("tempTimestamp is not null");
            return (Criteria) this;
        }

        public Criteria andTempTimestampEqualTo(Long value) {
            addCriterion("tempTimestamp =", value, "tempTimestamp");
            return (Criteria) this;
        }

        public Criteria andTempTimestampNotEqualTo(Long value) {
            addCriterion("tempTimestamp <>", value, "tempTimestamp");
            return (Criteria) this;
        }

        public Criteria andTempTimestampGreaterThan(Long value) {
            addCriterion("tempTimestamp >", value, "tempTimestamp");
            return (Criteria) this;
        }

        public Criteria andTempTimestampGreaterThanOrEqualTo(Long value) {
            addCriterion("tempTimestamp >=", value, "tempTimestamp");
            return (Criteria) this;
        }

        public Criteria andTempTimestampLessThan(Long value) {
            addCriterion("tempTimestamp <", value, "tempTimestamp");
            return (Criteria) this;
        }

        public Criteria andTempTimestampLessThanOrEqualTo(Long value) {
            addCriterion("tempTimestamp <=", value, "tempTimestamp");
            return (Criteria) this;
        }

        public Criteria andTempTimestampIn(List<Long> values) {
            addCriterion("tempTimestamp in", values, "tempTimestamp");
            return (Criteria) this;
        }

        public Criteria andTempTimestampNotIn(List<Long> values) {
            addCriterion("tempTimestamp not in", values, "tempTimestamp");
            return (Criteria) this;
        }

        public Criteria andTempTimestampBetween(Long value1, Long value2) {
            addCriterion("tempTimestamp between", value1, value2, "tempTimestamp");
            return (Criteria) this;
        }

        public Criteria andTempTimestampNotBetween(Long value1, Long value2) {
            addCriterion("tempTimestamp not between", value1, value2, "tempTimestamp");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampIsNull() {
            addCriterion("lastEndTimestamp is null");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampIsNotNull() {
            addCriterion("lastEndTimestamp is not null");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampEqualTo(Long value) {
            addCriterion("lastEndTimestamp =", value, "lastEndTimestamp");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampNotEqualTo(Long value) {
            addCriterion("lastEndTimestamp <>", value, "lastEndTimestamp");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampGreaterThan(Long value) {
            addCriterion("lastEndTimestamp >", value, "lastEndTimestamp");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampGreaterThanOrEqualTo(Long value) {
            addCriterion("lastEndTimestamp >=", value, "lastEndTimestamp");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampLessThan(Long value) {
            addCriterion("lastEndTimestamp <", value, "lastEndTimestamp");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampLessThanOrEqualTo(Long value) {
            addCriterion("lastEndTimestamp <=", value, "lastEndTimestamp");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampIn(List<Long> values) {
            addCriterion("lastEndTimestamp in", values, "lastEndTimestamp");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampNotIn(List<Long> values) {
            addCriterion("lastEndTimestamp not in", values, "lastEndTimestamp");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampBetween(Long value1, Long value2) {
            addCriterion("lastEndTimestamp between", value1, value2, "lastEndTimestamp");
            return (Criteria) this;
        }

        public Criteria andLastEndTimestampNotBetween(Long value1, Long value2) {
            addCriterion("lastEndTimestamp not between", value1, value2, "lastEndTimestamp");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumIsNull() {
            addCriterion("reachedBatchNum is null");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumIsNotNull() {
            addCriterion("reachedBatchNum is not null");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumEqualTo(Integer value) {
            addCriterion("reachedBatchNum =", value, "reachedBatchNum");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumNotEqualTo(Integer value) {
            addCriterion("reachedBatchNum <>", value, "reachedBatchNum");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumGreaterThan(Integer value) {
            addCriterion("reachedBatchNum >", value, "reachedBatchNum");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumGreaterThanOrEqualTo(Integer value) {
            addCriterion("reachedBatchNum >=", value, "reachedBatchNum");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumLessThan(Integer value) {
            addCriterion("reachedBatchNum <", value, "reachedBatchNum");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumLessThanOrEqualTo(Integer value) {
            addCriterion("reachedBatchNum <=", value, "reachedBatchNum");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumIn(List<Integer> values) {
            addCriterion("reachedBatchNum in", values, "reachedBatchNum");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumNotIn(List<Integer> values) {
            addCriterion("reachedBatchNum not in", values, "reachedBatchNum");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumBetween(Integer value1, Integer value2) {
            addCriterion("reachedBatchNum between", value1, value2, "reachedBatchNum");
            return (Criteria) this;
        }

        public Criteria andReachedBatchNumNotBetween(Integer value1, Integer value2) {
            addCriterion("reachedBatchNum not between", value1, value2, "reachedBatchNum");
            return (Criteria) this;
        }

        public Criteria andLoopNumIsNull() {
            addCriterion("loopNum is null");
            return (Criteria) this;
        }

        public Criteria andLoopNumIsNotNull() {
            addCriterion("loopNum is not null");
            return (Criteria) this;
        }

        public Criteria andLoopNumEqualTo(Integer value) {
            addCriterion("loopNum =", value, "loopNum");
            return (Criteria) this;
        }

        public Criteria andLoopNumNotEqualTo(Integer value) {
            addCriterion("loopNum <>", value, "loopNum");
            return (Criteria) this;
        }

        public Criteria andLoopNumGreaterThan(Integer value) {
            addCriterion("loopNum >", value, "loopNum");
            return (Criteria) this;
        }

        public Criteria andLoopNumGreaterThanOrEqualTo(Integer value) {
            addCriterion("loopNum >=", value, "loopNum");
            return (Criteria) this;
        }

        public Criteria andLoopNumLessThan(Integer value) {
            addCriterion("loopNum <", value, "loopNum");
            return (Criteria) this;
        }

        public Criteria andLoopNumLessThanOrEqualTo(Integer value) {
            addCriterion("loopNum <=", value, "loopNum");
            return (Criteria) this;
        }

        public Criteria andLoopNumIn(List<Integer> values) {
            addCriterion("loopNum in", values, "loopNum");
            return (Criteria) this;
        }

        public Criteria andLoopNumNotIn(List<Integer> values) {
            addCriterion("loopNum not in", values, "loopNum");
            return (Criteria) this;
        }

        public Criteria andLoopNumBetween(Integer value1, Integer value2) {
            addCriterion("loopNum between", value1, value2, "loopNum");
            return (Criteria) this;
        }

        public Criteria andLoopNumNotBetween(Integer value1, Integer value2) {
            addCriterion("loopNum not between", value1, value2, "loopNum");
            return (Criteria) this;
        }
    }

    public static class Criteria extends GeneratedCriteria {

        protected Criteria() {
            super();
        }
    }

    public static class Criterion {
        private String condition;

        private Object value;

        private Object secondValue;

        private boolean noValue;

        private boolean singleValue;

        private boolean betweenValue;

        private boolean listValue;

        private String typeHandler;

        public String getCondition() {
            return condition;
        }

        public Object getValue() {
            return value;
        }

        public Object getSecondValue() {
            return secondValue;
        }

        public boolean isNoValue() {
            return noValue;
        }

        public boolean isSingleValue() {
            return singleValue;
        }

        public boolean isBetweenValue() {
            return betweenValue;
        }

        public boolean isListValue() {
            return listValue;
        }

        public String getTypeHandler() {
            return typeHandler;
        }

        protected Criterion(String condition) {
            super();
            this.condition = condition;
            this.typeHandler = null;
            this.noValue = true;
        }

        protected Criterion(String condition, Object value, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.typeHandler = typeHandler;
            if (value instanceof List<?>) {
                this.listValue = true;
            } else {
                this.singleValue = true;
            }
        }

        protected Criterion(String condition, Object value) {
            this(condition, value, null);
        }

        protected Criterion(String condition, Object value, Object secondValue, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.secondValue = secondValue;
            this.typeHandler = typeHandler;
            this.betweenValue = true;
        }

        protected Criterion(String condition, Object value, Object secondValue) {
            this(condition, value, secondValue, null);
        }
    }
}