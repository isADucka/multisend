package com.yyyy.multisend.handler.dutyHandler.dudyHandlerImpl;


import cn.hutool.core.util.ReflectUtil;
import com.yyyy.multisend.common.enums.ReceiverType;
import com.yyyy.multisend.common.enums.ResponseCodeType;
import com.yyyy.multisend.common.models.Models;
import com.yyyy.multisend.common.povo.po.MessageModel;
import com.yyyy.multisend.common.povo.vo.MsgName;
import com.yyyy.multisend.common.ssm.MsgTask;
import com.yyyy.multisend.dao.Mapper.MessageModelDao;
import com.yyyy.multisend.dao.handler.DutyChain;
import com.yyyy.multisend.handler.dutyHandler.Duty;
import com.yyyy.multisend.handler.utils.PlaceHolderUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author isADuckA
 * @Date 2023/4/10 20:14
 * 处理MsgTask里面的值，比如说判断短信的签名和模板问题
 * 还有发送的对象和内容的值是否符合规范
 *
 */
@Service
public class AfterCheckDuty implements Duty {

    @Autowired
    private MessageModelDao messageModelDao;

    @Override
    public DutyChain porcess(DutyChain dutyChain) {
        MsgTask msgTask = dutyChain.getMsgTask();
        //查到这个modelId是否存在
        MessageModel messageModel = messageModelDao.findById(msgTask.getModelId()).get();
        //设置接收者类型
        msgTask.setReceiverType(messageModel.getReceiverType());
        //设置消息类型，营销类，验证码类，通知类
        msgTask.setMsgType(messageModel.getMsgType());
        if(messageModel==null){
            //说明这个模板不存在，返回错误
            dutyChain.setOver(true);
            //这里是模板为null
            dutyChain.setResponse(ResponseCodeType.DUTY_ERROR_MSGTASKNULL);
            return dutyChain;
        }
        //如果存在，就要判断输入的对象是否符合规范

//        dutyChain.setOver(true);
//        MsgTask msgTask = dutyChain.getMsgTask();
//
        //判断发送对象的规范
        if(!judge(messageModel.getReceiverType(),msgTask.getReceiver())){
            dutyChain.setResponse(ResponseCodeType.DUTY_ERROR_MSGTASKNULL);
            return dutyChain;
        }

        //处理参数，也就是占位符的问题
      replaceThePlaceHolder(messageModel, msgTask);


        //这里有一个要强调的是，如果是短信类型的，还需要处理参数拼接
        //如果发送方式是手机，证明是短信，短信需要拼接验证码，其他的目前还不用
        if(messageModel.getReceiverType().equals(ReceiverType.PHONE.getCode())){

            String code = msgTask.getParm().get(MsgName.MSGCONTENT);
            msgTask.getParm().put(MsgName.MSGCONTENT,"{\"code\":\""+code+"\"}");
            System.out.println(msgTask.getParm());
        }

        dutyChain.setMsgTask(msgTask);
        //结束
        dutyChain.setOver(false);
        dutyChain.setResponse(ResponseCodeType.DUTY_SUCCESS);
        return  dutyChain;

    }


    /**
     * 利用正则表达式判断输入对象是否规范
     * @param code 真则表达式
     * @param judgeNumerber 被判断的对象
     * @return
     */
    public boolean judge(Integer code, Set<String> judgeNumerber){

        for(String user:judgeNumerber){
            //获取ruler
            String ruler = ReceiverType.getRuler(code);
            //根绝ruler来判断接收者的输入是否合法
            Matcher matcher = Pattern.compile(ruler).matcher(user);
            //只有有一个不符合就终止
            if(!matcher.matches()){
               return false;
            }
        }

        return  true;
    }


    /**
     * 解决占位符问题
     * @param messageModel
     * @param msgTask
     * @return
     */
    public MsgTask replaceThePlaceHolder(MessageModel messageModel,MsgTask msgTask)  {
        //获取的用户传进来的参数
        Map<String, String> parm = msgTask.getParm();
        //先根据receiverType的值来获取相对应的class
        Class<? extends Models> model = ReceiverType.getClassByCode(msgTask.getReceiverType());
        Models models = ReflectUtil.newInstance(model);
        Map<String,String> resultParm=new HashMap<>();

        //根据反射获取实例
        Field[] fields = ReflectUtil.getFields(model);
        for (Field field:fields){
            String fieldName = field.getName();
            System.out.println("字段名称："+fieldName);
            try {
                Field fieldValue = messageModel.getClass().getDeclaredField(fieldName);
                fieldValue.setAccessible(true);
                //获取具有占位符的字段值
                Object o = fieldValue.get(messageModel);
                if(o==null){
                    continue;
                }
                System.out.println(o);
                //替换占位符
                String replace = PlaceHolderUtils.replace((String) o, parm);
                ReflectUtil.setFieldValue(models,field,replace);
                System.out.println("替代后"+replace);
                //把值塞回去
                resultParm.put(fieldName,replace);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }

        }
//        考虑一个个传参然后拼接？
//        PlaceHolderUtils.replace("具有占位符的值",parm);
//        msgTask.setModels(models);
        //这里我打算将这个值转换为一个String,然后再变成一个map;
        msgTask.setParm(resultParm);
        return  msgTask;
    }

//    public static void main(String[] args) {
//        MessageModel messageModel=new MessageModel();
//        messageModel.setTitle("{$title}");
//        messageModel.setMsgContent("{$content}");
//        messageModel.setUrl("{$url}");
//
//
//        HashMap<String,String> map=new HashMap<>();
//        map.put("title","爪哇");
//        map.put("content","考核结束");
//        map.put("url","iiii");
//
//        MsgTask msgTask=MsgTask.builder()
//                .parm(map)
//                .receiverType(1004)
//                .build();
//
//        Models models = new AfterCheckDuty().replaceThePlaceHolder(messageModel, msgTask);
//
//
//    }


}
