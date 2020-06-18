package com.company.touch;

import java.net.InetAddress;

public class EsptouchGenerator {
    /**
     * Constructor of EsptouchGenerator, it will cost some time(maybe a bit
     * much)
     *
     * @param apSsid      the Ap's ssid
     * @param apBssid     the Ap's bssid
     * @param apPassword  the Ap's password
     * @param inetAddress the phone's or pad's local ip address allocated by Ap
     */
    public EsptouchGenerator(byte[] apSsid, byte[] apBssid, byte[] apPassword, InetAddress inetAddress,
                             ITouchEncryptor encryptor) {

        // generate data code
        DatumCode dc = new DatumCode(apSsid, apBssid, apPassword, inetAddress, encryptor);
        char[] dcU81 = dc.getU8s();
        mDcBytes2 = new byte[dcU81.length][];

        for (int i = 0; i < mDcBytes2.length; i++) {
            mDcBytes2[i] = ByteUtil.genSpecBytes(dcU81[i]);
        }
    }

    /**
     * Constructor of DatumCode
     *
     * @param apSsid      the Ap's ssid
     * @param apBssid     the Ap's bssid
     * @param apPassword  the Ap's password
     * @param ipAddress   the ip address of the phone or pad
     * @param encryptor null use origin data, not null use encrypted data
     */
    private DatumCode(byte[] apSsid, byte[] apBssid, byte[] apPassword,
                     InetAddress ipAddress, ITouchEncryptor encryptor) {
        // Data = total len(1 byte) + apPwd len(1 byte) + SSID CRC(1 byte) +
        // BSSID CRC(1 byte) + TOTAL XOR(1 byte)+ ipAddress(4 byte) + apPwd + apSsid apPwdLen <=
        // 105 at the moment

        // total xor
        char totalXor = 0;

        char apPwdLen = (char) apPassword.length;
        CRC8 crc = new CRC8();
        crc.update(apSsid);
        update(apSsid, 0, apSsid.length);
        char apSsidCrc = (char) crc.getValue();

        crc.reset();
        crc.update(apBssid);
        char apBssidCrc = (char) crc.getValue();

        char apSsidLen = (char) apSsid.length;

        byte[] ipBytes = ipAddress.getAddress();
        int ipLen = ipBytes.length;

        char totalLen = (char) (EXTRA_HEAD_LEN + ipLen + apPwdLen + apSsidLen);
        // ^= 异或就是两个数的二进制形式，按位对比，相同取0，不同取一
        // build data codes
        mDataCodes = new LinkedList<>();
        mDataCodes.add(new DataCode(totalLen, 0));
        totalXor ^= totalLen;
        mDataCodes.add(new DataCode(apPwdLen, 1));
        totalXor ^= apPwdLen;
        mDataCodes.add(new DataCode(apSsidCrc, 2));
        totalXor ^= apSsidCrc;
        mDataCodes.add(new DataCode(apBssidCrc, 3));
        totalXor ^= apBssidCrc;
        // ESPDataCode 4 is null
        for (int i = 0; i < ipLen; ++i) {
            char c = ByteUtil.convertByte2Uint8(ipBytes[i]);
            totalXor ^= c;
            mDataCodes.add(new DataCode(c, i + EXTRA_HEAD_LEN));
        }

        for (int i = 0; i < apPassword.length; i++) {
            char c = ByteUtil.convertByte2Uint8(apPassword[i]);
            totalXor ^= c;
            mDataCodes.add(new DataCode(c, i + EXTRA_HEAD_LEN + ipLen));
        }

        // totalXor will xor apSsidChars no matter whether the ssid is hidden
        for (int i = 0; i < apSsid.length; i++) {
            char c = ByteUtil.convertByte2Uint8(apSsid[i]);
            totalXor ^= c;
            mDataCodes.add(new DataCode(c, i + EXTRA_HEAD_LEN + ipLen + apPwdLen));
        }

        // add total xor last
        mDataCodes.add(4, new DataCode(totalXor, 4));

        // add bssid
        int bssidInsertIndex = EXTRA_HEAD_LEN;
        for (int i = 0; i < apBssid.length; i++) {
            int index = totalLen + i;
            char c = ByteUtil.convertByte2Uint8(apBssid[i]);
            DataCode dc = new DataCode(c, index);
            if (bssidInsertIndex >= mDataCodes.size()) {
                mDataCodes.add(dc);
            } else {
                mDataCodes.add(bssidInsertIndex, dc);
            }
            bssidInsertIndex += 4;
        }
    }

    // ^运算符跟 | 类似，但有一点不同的是 如果两个操作位都为1的话，结果产生0
    private void update(byte[] buffer, int offset, int len) {
        for (int i = 0; i < len; i++) {
            int data = buffer[offset + i] ^ value;
            value = (short) (crcTable[data & 0xff] ^ (value << 8));
        }
    }
}
