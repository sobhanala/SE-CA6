package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private OrderBook orderBook = new OrderBook();
    @Builder.Default
    @Setter
    private int lastTradedPrice = 0;


    private void validateOrderForUpdate(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException {
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        if (order.getMinimumExpectedQuantity() != updateOrderRq.getMinimumExecutionQuantity()) {
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        }
    }
    private Order getOrderForUpdate(EnterOrderRq updateOrderRq) throws InvalidRequestException {
        Order order;
        if (updateOrderRq.getStopPrice() > 0)
            order = orderBook.findByOrderIdInInactiveQueue(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        else
            order = orderBook.findByOrderIdInActiveQueue(updateOrderRq.getSide(), updateOrderRq.getOrderId());

        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        return order;
    }
    private boolean hasDealerEnoughCreditOrSecurity(Order order){
        if(order.getSide()==Side.BUY)
            return order.getBroker().hasEnoughCredit(order.getValue());
        else
            return order.getShareholder().hasEnoughPositionsOn(order.getSecurity(), order.getQuantity());
    }

    private MatchResult handleInactiveOrder(Order order){
        if (order.getSide()==Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());
        orderBook.enqueueToInactiveQueue(order);
        return MatchResult.inActiveOrderEnqueued();
    }


    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (enterOrderRq.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();
        Order order;
        if (enterOrderRq.getPeakSize() == 0 && enterOrderRq.getStopPrice() == 0)
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(), OrderStatus.NEW, enterOrderRq.getMinimumExecutionQuantity());
        else if(enterOrderRq.getPeakSize() != 0)
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), OrderStatus.NEW, enterOrderRq.getMinimumExecutionQuantity());
        else {
            order = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getStopPrice());
            if (!hasDealerEnoughCreditOrSecurity(order)) {
                return (order.getSide() == Side.BUY) ? MatchResult.notEnoughCredit() : MatchResult.notEnoughPositions();
            }
            if (!order.canBeActive(lastTradedPrice))
                return handleInactiveOrder(order);
        }
        return matcher.execute(order);

    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = orderBook.findOrderInAllQueues(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());

        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        orderBook.removeByOrderFromBothQueues(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
    }


    private boolean hasEnoughPositionsToUpdate(Order order, EnterOrderRq updateOrderRq) {
        return order.getShareholder().hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity());
    }

    private boolean checkPriorityLoss(Order order, EnterOrderRq updateOrderRq) {
        return order.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != order.getPrice()
                || (order instanceof IcebergOrder icebergOrder && icebergOrder.getPeakSize() < updateOrderRq.getPeakSize());
    }

    private void increaseBrokerCredit(Order order) {
        order.getBroker().increaseCreditBy(order.getValue());
    }

    private void handleNoPriorityLoss(Order order) {
        if (order.getSide() == Side.BUY) {
            order.getBroker().decreaseCreditBy(order.getValue());
        }
    }

    private MatchResult handleInactiveOrderInQueue(Order order, EnterOrderRq updateOrderRq) {
        if (updateOrderRq.getSide() == Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());
        orderBook.enqueueToInactiveQueue(order);
        return MatchResult.inActiveOrderEnqueued();
    }


    private MatchResult executeMatching(Matcher matcher, Order order, Order originalOrder, EnterOrderRq updateOrderRq) {
        MatchResult matchResult = matcher.execute(order);
        if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
            orderBook.enqueueToActiveQueue(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY) {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
        }
        return matchResult;
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {

        Order order = getOrderForUpdate(updateOrderRq);

        if (order.getMinimumExpectedQuantity() != updateOrderRq.getMinimumExecutionQuantity()){
            return MatchResult.cantUpdateMinQuantity();
        }
        validateOrderForUpdate(order, updateOrderRq);

        if (updateOrderRq.getSide() == Side.SELL) {
            if (!hasEnoughPositionsToUpdate(order, updateOrderRq)) {
                return MatchResult.notEnoughPositions();
            }
        }

        boolean losesPriority = checkPriorityLoss(order, updateOrderRq);

        if (updateOrderRq.getSide() == Side.BUY) {
            increaseBrokerCredit(order);
        }

        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);

        if (!losesPriority && updateOrderRq.getStopPrice() == 0) {
            handleNoPriorityLoss(order);
            return MatchResult.executed(null, List.of());
        } else {
            order.markAsUpdating();
        }

        if (updateOrderRq.getStopPrice() > 0) {
            orderBook.removeByOrderIdFromInactiveQueue(updateOrderRq.getSide(), updateOrderRq.getOrderId());
            if (!order.canBeActive(lastTradedPrice))
                return handleInactiveOrderInQueue(order, updateOrderRq);
        } else {
            orderBook.removeByOrderIdFromActiveQueue(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        }

        return executeMatching(matcher, order, originalOrder, updateOrderRq);
    }


}
