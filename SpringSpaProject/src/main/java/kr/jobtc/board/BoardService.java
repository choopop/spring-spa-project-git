package kr.jobtc.board;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import kr.jobtc.mybatis.BoardMapper;

@Transactional
@Service
public class BoardService {
	PageVo pVo;
	
	public PageVo getpVo() {return pVo;}
	
	@Autowired
	PlatformTransactionManager manager;
	TransactionStatus status;
	
	@Autowired
	BoardMapper mapper;
	
	Object savePoint;
	
	public boolean insertR(BoardVo vo) {
		status = manager.getTransaction(new DefaultTransactionDefinition());
		savePoint = status.createSavepoint();
		boolean flag = true;
		
		int cnt = mapper.insertR(vo);
		if(cnt<1) {
			//status.rollbackToSavepoint(savePoint);
			flag=false;
		}else if(vo.getAttList().size()>0) {
			int attCnt = mapper.insertAttList(vo.getAttList());
			if(attCnt<0) flag=false;
		}
		if(flag) {
			manager.commit(status);
		}else {
			status.rollbackToSavepoint(savePoint);
			
			String[] delFiles = new String[vo.getAttList().size()];
			for(int i=0; i<vo.getAttList().size(); i++) {
				delFiles[i] = vo.getAttList().get(i).getSysFile();
			}
			fileDelete(delFiles);
		}
		
		return flag;
	}
	
	public void insertAttList(List<AttVo> attList) {
		
		int cnt = mapper.insertAttList(attList);
		if(cnt>0) {
			manager.commit(status);
		}else {
			status.rollbackToSavepoint(savePoint);
		}
		
	}
	
	public boolean replR(BoardVo vo) {
		status = manager.getTransaction(new DefaultTransactionDefinition());
		savePoint = status.createSavepoint();
		
		boolean b = true;
		mapper.seqUp(vo);
		int cnt = mapper.replR(vo);
		if(cnt<1) {
			b=false;
		}else if(vo.getAttList().size()>0) {
			int attCnt = mapper.insertAttList(vo.getAttList());
			if(attCnt<0) b=false;
		}
		
		if(b) manager.commit(status);
		else {
			status.rollbackToSavepoint(savePoint);
			
			String[] delFiles = new String[vo.getAttList().size()];
			for(int i=0; i<vo.getAttList().size(); i++) {
				delFiles[i] = vo.getAttList().get(i).getSysFile();
			}
			fileDelete(delFiles);
		
		}
		return b;
	}
	
	 public boolean updateR(BoardVo bVo, String[] delFiles) {
		status = manager.getTransaction(new DefaultTransactionDefinition());
		savePoint = status.createSavepoint();
	    
		boolean b=true;
	    int cnt = mapper.update(bVo);
	    if(cnt<1) {
	        b=false;
	    }else if(bVo.getAttList().size()>0) {
	        int attCnt = mapper.attUpdate(bVo);	//???????????? update??? ????????????. insert??? ??????* (int attCnt = session.update("board.attUpdate", bVo);
	        if(attCnt<1) {
	        	b=false;
	        }
	    }
	       
	        
	    if(b) {
	    		manager.commit(status);
	            if(delFiles !=null && delFiles.length>0) {
	                // ?????? ?????? ????????? ??????
	                cnt = mapper.attDelete(delFiles);
	                if(cnt>0) {
	                    fileDelete(delFiles); // ?????? ??????
	                }else {
	                    b=false;
	                }
	            }
	        }else {
	        	status.rollbackToSavepoint(savePoint);
	        	
	        	delFiles = new String[bVo.getAttList().size()];
	        	for(int i=0; i<bVo.getAttList().size(); i++) {
	        		delFiles[i] = bVo.getAttList().get(i).getSysFile();
	        	}
	        	fileDelete(delFiles);
	        }
	        return b;
	 }
	
	public List<BoardVo> select2(PageVo pVo){
		int totSize = mapper.totList(pVo);
		pVo.setTotSize(totSize);
		this.pVo = pVo;	//??????????????? sql???????????? ????????? ?????? ????????? pVo??? this.pVo??? ???????????? ????????? list??? ????????????????????? getpVo?????? pVo??? ?????? ??? ?????????
		List<BoardVo> list = null;
		list = mapper.select2(pVo);
		return list;
	}
	
	public List<BoardVo> board10(){
		List<BoardVo> list = null;
		list = mapper.board10();
		return list;
	}
	
	public BoardVo view(int sno, String up) {
		BoardVo bVo =null;
		if(up != null && up.equals("up")) {
			mapper.hitUp(sno);
		}
		bVo = mapper.view(sno);
		List<AttVo> attList = mapper.attList(sno);
		bVo.setAttList(attList);
		
		return bVo;
	}
	
	//SPABoard Dao ??????
	public boolean delete(BoardVo bVo) {
		boolean b = true;
		
		//????????? ?????? ????????? ????????? ????????????
		//?????? grp?????? ????????? seq?????? 1??? ??? seq??? ????????????
		//deep??? ?????? ?????? ??? ?????? ????????? ????????? ?????? ??????.
		int replCnt = mapper.replCheck(bVo);
		
		if(replCnt>0) {
			b=false;
			return b;
		}
		//sno??? ???????????? ????????? ??????
		status = manager.getTransaction(new DefaultTransactionDefinition());
		Object savePoint = status.createSavepoint();
		
		int cnt = mapper.delete(bVo);
		if(cnt<1) {
			b=false;
		}else {
			//sno??? pSno??? ????????? ?????? ??????????????? ???????????? ?????? ????????????
			List<String> attList = mapper.delFileList(bVo.getSno());
			//??????????????? ??????
			if(attList.size()>0) {
				cnt = mapper.attDeleteAll(bVo.getSno());
				if(cnt>0) {
					//???????????? ??????
					if(attList.size()>0) {
						String[] delList = attList.toArray(new String[0]);
						System.out.println(("delList : ")+  Arrays.toString(delList));
						fileDelete(delList);
					}
				}else {
					b=false;
				}
			}
		}
		
		if(b) manager.commit(status);
		
		else status.rollbackToSavepoint(savePoint);
		
		return b;
	}
	
	public void fileDelete(String[] delFiles) {
		
		  for(String f : delFiles){
		  File file = new File(FileUploadController.path + f);
		  if(file.exists()) file.delete();
		  }
		 
	}
	
	//???????????????????????????select?????????
	public List<BoardVo> select(String findStr){
		List<BoardVo> list = null;
		list = mapper.select(findStr);
		return list;
	}
}

