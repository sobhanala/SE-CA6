package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

@Getter
public class OrderBook {
    private final LinkedList<Order> buyQueue;
    private final LinkedList<Order> sellQueue;
    private final LinkedList<Order> inactiveBuyQueue;
    private final LinkedList<Order> inactiveSellQueue;

    public OrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
        inactiveBuyQueue = new LinkedList<>();
        inactiveSellQueue = new LinkedList<>();
    }

    private LinkedList<Order> getQueue(Side side) {
        return side == Side.BUY ? buyQueue : sellQueue;
    }

    private LinkedList<Order> getInactiveQueue(Side side){
        return side == Side.BUY ? inactiveBuyQueue : inactiveSellQueue;
    }

    public Order matchWithFirst(Order newOrder) {
        var queue = getQueue(newOrder.getSide().opposite());
        if (newOrder.matches(queue.getFirst()))
            return queue.getFirst();
        else
            return null;
    }

    public void putBack(Order order) {
        LinkedList<Order> queue = getQueue(order.getSide());
        order.queue();
        queue.addFirst(order);
    }

    public void restoreOrder(Order order) {
        removeByOrderIdFromActiveQueue(order.getSide(), order.getOrderId());
        putBack(order);
    }

    public boolean hasOrderOfType(Side side) {
        return !getQueue(side).isEmpty();
    }

    public void removeFirst(Side side) {
        getQueue(side).removeFirst();
    }

    public int totalSellQuantityByShareholder(Shareholder shareholder) {
        return sellQueue.stream()
                .filter(order -> order.getShareholder().equals(shareholder))
                .mapToInt(Order::getTotalQuantity)
                .sum();
    }

    public void enqueueInGivenQueue(Order order, List<Order> queue, Predicate<Order> condition) {
        ListIterator<Order> it = queue.listIterator();
        while (it.hasNext()) {
            Order nextOrder = it.next();
            if (condition.test(nextOrder)) {
                it.previous();
                break;
            }
        }
        order.queue();
        it.add(order);
    }

    public void enqueueToInactiveQueue(Order order) {
        List<Order> queue = getInactiveQueue(order.getSide());
        Predicate<Order> condition = order::inActievOrderQueuesBefore;
        enqueueInGivenQueue(order, queue, condition);
    }

    public void enqueueToActiveQueue(Order order) {
        List<Order> queue = getQueue(order.getSide());
        Predicate<Order> condition = order::queuesBefore;
        enqueueInGivenQueue(order, queue, condition);
    }


    public Order dequeueFromInactiveQueue(Side side, int lastTradedPrice) {
        ListIterator<Order> it = getInactiveQueue(side).listIterator();
        if (it.hasNext()) {
            Order order = it.next();
            if(order.canBeActive(lastTradedPrice)) {
                it.remove();
                return order;
            }
        }
        return null;
    }

    public Order findByOrderIdInActiveQueue(Side side, long orderId) {
        return findByOrderIdInGivenQueue(orderId, getQueue(side));
    }

    public Order findByOrderIdInInactiveQueue(Side side, long orderId) {
        return findByOrderIdInGivenQueue(orderId, getInactiveQueue(side));
    }

    private Order findByOrderIdInGivenQueue( long orderId, LinkedList<Order> queue) {
        for (Order order : queue) {
            if (order.getOrderId() == orderId)
                return order;
        }
        return null;
    }

    public Order findOrderInAllQueues(Side side, long orderId){
        Order orderInInActiveQueue = findByOrderIdInInactiveQueue(side, orderId);
        if(orderInInActiveQueue!=null)
            return orderInInActiveQueue;
        return findByOrderIdInActiveQueue(side, orderId);
    }
    private void removeByOrderIdFromGivenQueue(long orderId, LinkedList<Order> queue){
        Iterator<Order> it = queue.listIterator();
        while (it.hasNext()) {
            if (it.next().getOrderId() == orderId) {
                it.remove();
                break;
            }
        }
    }
    public void removeByOrderIdFromActiveQueue(Side side, long orderId) {
        removeByOrderIdFromGivenQueue(orderId,getQueue(side));
    }
    public void removeByOrderIdFromInactiveQueue(Side side, long orderId) {
        removeByOrderIdFromGivenQueue(orderId,getInactiveQueue(side));
    }
    public void removeByOrderFromBothQueues(Side side, long orderId){
        removeByOrderIdFromActiveQueue(side, orderId);
        removeByOrderIdFromInactiveQueue(side, orderId);
    }

}
