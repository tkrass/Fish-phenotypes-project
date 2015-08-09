#read in file line
all.files=read.delim("~/project/all_files", header=FALSE)
filelocations=unique(sapply(all.files[,10],as.character))
#extract file names
#assume all files end with .xml
wherelastslash=vector()
whichposition=vector()
filenames=vector()
df=data.frame()
for(i in 1:length(filelocations)){
  wherelastslash[i]=length(gregexpr("/",filelocations[i])[[1]])
  whichposition[i]=gregexpr("/",filelocations[i])[[1]][wherelastslash[i]]
  filenames[i]=substring(filelocations[i],whichposition[i]+1,nchar(filelocations[i])-4)
}
nfiles=length(filenames)
for(it in 1:nfiles){
  print('File name:')
  print(filenames[it])
  print(paste("File number", it, "out of", nfiles))
  file=as.data.frame(all.files)
  file=file[which(file[,10]==filelocations[it]),]
  df=file
  nameofoutputfile=paste("/home/tadek/project/Differential phenotype profiles/",filenames[it],sep='')
  colnames(file)=c('taxon.id','character.id','state.id','taxon.label',
                 'pheno.entity','pheno.e2','pheno.quality','trait',
                 'label','file location')
  chars=unique(as.character(file$character.id))
  nchars=length(chars)
  taxa=unique(as.character(file$taxon.id))
  ntaxa=length(taxa)
  if(ntaxa<=1){
    print('Fewer than 2 taxa: aborting')
  }
  else{
    M=matrix('',ntaxa,nchars)
    colnames(M)=c(chars)
    rownames(M)=taxa
    for (i in 1:ntaxa){
      temp=file[which(as.character(file[,1])==rownames(M)[i]),]
      temp=data.frame(lapply(temp,as.character),stringsAsFactors=FALSE)
      d=dim(temp)[1]
      j=1
      while (j <= d){
        wh=which(temp[,2]==temp[j,2])
        wh=wh[-1]
        if(length(wh)>0){
          temp=temp[-wh,]
          d=dim(temp)[1]
        }
        j=j+1
      }
      for (j in 1:d){
        M[i,temp[j,2]]=temp[j,3]
      }
    }
#    N=M[c('dfd4eb27-be7b-4345-8d0d-075a78fb8ac0','4d63913b-93c2-4f64-a45f-06ddcedcd84d','6f008bf0-f91e-497c-9022-187bce6c413b'),]
    diffonepair=function(taxon1,taxon2){
      w=which(M[taxon1,]!=M[taxon2,] & M[taxon1,]!='' & M[taxon2,]!='')
      colnames(M)[w]
    }
    diffprofile=as.list(numeric(ntaxa*ntaxa))
    dim(diffprofile)=c(ntaxa,ntaxa)
    rownames(diffprofile)=taxa
    colnames(diffprofile)=taxa
    for(i in 1:(ntaxa-1)){
      diffprofile[[i,i]]=NA
      for (j in (i+1):ntaxa){
        diffprofile[[i,j]]=diffonepair(taxa[i],taxa[j])
        diffprofile[[j,i]]=diffprofile[[i,j]]
      }
    }
    diffprofile[[ntaxa,ntaxa]]=NA
    writeone=function(taxon1,taxon2){
      chara=diffprofile[[taxon1,taxon2]]
      if(length(chara)>0){
          for(j in 1:length(chara)){
          wh1=which(as.character(file[,1])==taxon1 & as.character(file[,2])==chara[j])
          wh2=which(as.character(file[,1])==taxon2 & as.character(file[,2])==chara[j])
          line1=wh1[1]
          line2=wh2[1]
          p1=paste(lapply(file[line1,],as.character))
          p2=paste(lapply(file[line2,],as.character))
          text=paste(p1[4], p2[4], p1[5], p1[6], p1[8], p1[9],
                     p2[8], p2[9],sep="\t")
          cat(c(text,"\n"),file=nameofoutputfile,sep="\t",append=TRUE)
        }
      }
    }
    makefullprofile=function(){
      print('Writing differential phenotype profile to file')
      header=paste("Taxon 1", "Taxon 2", "Character", "e2",
                   "Trait in taxon 1 - id", "Trait in taxon 1 - text",
                   "Trait in taxon 2 - id","Trait in taxon 2 - text",
                   sep="\t")
      cat(c(header,"\n"),file=nameofoutputfile,sep="\t",append=FALSE)
      howman=ntaxa/20
      byb=round((ntaxa-1)/howman)
      currr=1
      for(i in 1:(ntaxa-1)){
        #print progress within one file
        if((i-1)<currr*byb && i>=currr*byb){
          print(paste(round(100*i/(ntaxa-1)),"%",sep=''))
          currr=currr+1
        }
        for(j in (i+1):ntaxa){
          chara=diffprofile[[taxa[i],taxa[j]]]
          if(length(chara)>0){
          }
          writeone(taxa[i],taxa[j])
        }
      }
    }
    makefullprofile()
}
}
print("All files processed. Well done Tadek.")